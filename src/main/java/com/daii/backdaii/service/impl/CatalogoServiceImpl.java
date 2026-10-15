package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.cobro.Tarifario;
import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.Horario;
import com.daii.backdaii.domain.disponibilidad.MotorDisponibilidad;
import com.daii.backdaii.domain.disponibilidad.Ocupacion;
import com.daii.backdaii.domain.disponibilidad.ReglasReserva;
import com.daii.backdaii.domain.entity.Bloqueo;
import com.daii.backdaii.domain.entity.Edificio;
import com.daii.backdaii.domain.entity.Empresa;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Piso;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.ServicioPiso;
import com.daii.backdaii.domain.enums.EstadoEspacio;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.domain.repository.AreaPlanoRepository;
import com.daii.backdaii.domain.repository.BloqueoRepository;
import com.daii.backdaii.domain.repository.EdificioRepository;
import com.daii.backdaii.domain.repository.EmpresaRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.EspacioUbicacion;
import com.daii.backdaii.domain.repository.PisoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.ServicioPisoRepository;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.*;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.service.CatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoServiceImpl implements CatalogoService {

    /** How far "Próximo horario libre" looks ahead. */
    private static final int DIAS_BUSQUEDA = 14;
    /** Length of the slot the plan opens with when the user did not pick one. */
    private static final Duration FRANJA_POR_DEFECTO = Duration.ofHours(2);
    private static final int MAXIMO_DIAS_AGENDA = 42;

    private final EmpresaRepository empresaRepository;
    private final EdificioRepository edificioRepository;
    private final PisoRepository pisoRepository;
    private final EspacioRepository espacioRepository;
    private final ServicioPisoRepository servicioPisoRepository;
    private final AreaPlanoRepository areaPlanoRepository;
    private final ReservaRepository reservaRepository;
    private final BloqueoRepository bloqueoRepository;
    private final Ocupaciones ocupaciones;
    private final MotorDisponibilidad motor;
    private final ReglasReserva reglas;
    private final Tarifario tarifario;
    private final Clock reloj;

    // ---- Empresas and edificios -------------------------------------------------------------

    @Override
    public List<EmpresaResumen> listarEmpresas() {
        LocalDateTime ahora = ahora();
        List<Edificio> edificios = edificioRepository.findAllConEmpresa();
        List<EspacioUbicacion> ubicaciones = espacioRepository.findUbicaciones();
        Map<Long, List<Ocupacion>> ocupadas = ocupaciones.deTodos(inicioDelDia(ahora), inicioDelDia(ahora).plusDays(2));

        Map<Long, EdificioResumen> resumenes = resumirEdificios(edificios, ubicaciones, ocupadas, ahora);
        Map<Long, List<Edificio>> porEmpresa = edificios.stream()
                .collect(Collectors.groupingBy(e -> e.getEmpresa().getId()));

        return empresaRepository.findAll(Sort.by("nombre")).stream()
                .map(empresa -> resumirEmpresa(empresa, porEmpresa.getOrDefault(empresa.getId(), List.of()), resumenes))
                .toList();
    }

    @Override
    public EmpresaDetalle obtenerEmpresa(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        LocalDateTime ahora = ahora();
        List<Edificio> edificios = edificioRepository.findByEmpresaIdOrderByNombre(empresaId);
        List<EspacioUbicacion> ubicaciones = espacioRepository.findUbicacionesDeEmpresa(empresaId);
        Map<Long, List<Ocupacion>> ocupadas = ocupaciones.porEspacio(
                ubicaciones.stream().map(EspacioUbicacion::espacioId).toList(),
                inicioDelDia(ahora), inicioDelDia(ahora).plusDays(2));

        Map<Long, EdificioResumen> resumenes = resumirEdificios(edificios, ubicaciones, ocupadas, ahora);
        return new EmpresaDetalle(
                resumirEmpresa(empresa, edificios, resumenes),
                edificios.stream().map(e -> resumenes.get(e.getId())).toList());
    }

    @Override
    public EdificioDetalle obtenerEdificio(Long edificioId) {
        Edificio edificio = edificioRepository.findConEmpresaById(edificioId)
                .orElseThrow(() -> new NotFoundException("Edificio no encontrado"));
        LocalDateTime ahora = ahora();
        Horario horario = edificio.getHorario();

        List<Piso> pisos = pisoRepository.findByEdificioIdOrderByNumeroDesc(edificioId);
        List<Long> pisoIds = pisos.stream().map(Piso::getId).toList();
        Map<Long, List<Espacio>> espaciosPorPiso = espacioRepository.findByPisoIdInOrderByCodigo(pisoIds).stream()
                .collect(Collectors.groupingBy(e -> e.getPiso().getId()));
        Map<Long, List<ServicioPiso>> serviciosPorPiso = servicioPisoRepository.findByPisoIdIn(pisoIds).stream()
                .collect(Collectors.groupingBy(s -> s.getPiso().getId()));

        boolean abierto = estaAbierto(horario, ahora);
        Franja franja = franjaAhora(horario, ahora);
        List<Long> espacioIds = espaciosPorPiso.values().stream().flatMap(List::stream).map(Espacio::getId).toList();
        Map<Long, List<Ocupacion>> ocupadas = ocupaciones.porEspacio(espacioIds, franja.inicio(), franja.fin());

        List<PisoResumen> resumenes = pisos.stream().map(piso -> {
            List<Espacio> espacios = espaciosPorPiso.getOrDefault(piso.getId(), List.of());
            int cubiculos = (int) espacios.stream().filter(e -> e.getTipo() == TipoEspacio.CUBICULO).count();
            int libres = (int) espacios.stream()
                    .filter(e -> motor.estado(Ocupaciones.de(ocupadas, e.getId()), franja) == EstadoEspacio.LIBRE)
                    .count();
            return new PisoResumen(piso.getId(), piso.getNumero(), piso.getNombre(), espacios.size(), cubiculos,
                    espacios.size() - cubiculos,
                    Vistas.comodidades(piso, serviciosPorPiso.getOrDefault(piso.getId(), List.of())), libres,
                    piso.getDisposicion().getEtiqueta());
        }).toList();

        EdificioResumen resumen = resumirEdificios(List.of(edificio), espacioRepository.findUbicacionesDeEdificio(edificioId),
                ocupaciones.porEspacio(espacioIds, inicioDelDia(ahora), inicioDelDia(ahora).plusDays(2)), ahora)
                .get(edificioId);

        return new EdificioDetalle(resumen, new Ref(edificio.getEmpresa().getId(), edificio.getEmpresa().getNombre()),
                FranjaDTO.de(franja), abierto, resumenes.stream().mapToInt(PisoResumen::libres).sum(), resumenes);
    }

    @Override
    public List<EdificioConPisos> listarEdificiosConPisos() {
        List<Edificio> edificios = edificioRepository.findAllConEmpresa();
        Map<Long, List<Piso>> pisosPorEdificio = pisoRepository
                .findByEdificioIdInOrderByNumeroDesc(edificios.stream().map(Edificio::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getEdificio().getId()));
        return edificios.stream()
                .map(e -> new EdificioConPisos(e.getId(), e.getNombre(), e.getEmpresa().getNombre(),
                        e.getHoraApertura(), e.getHoraCierre(),
                        pisosPorEdificio.getOrDefault(e.getId(), List.of()).stream()
                                .map(p -> new PisoRef(p.getId(), p.getNumero(), p.getNombre()))
                                .toList()))
                .toList();
    }

    // ---- Floor plan -------------------------------------------------------------------------

    @Override
    public PlanoPiso obtenerPlano(Long pisoId, FiltroPlano filtro) {
        Piso piso = pisoRepository.findConEdificioById(pisoId)
                .orElseThrow(() -> new NotFoundException("Piso no encontrado"));
        Edificio edificio = piso.getEdificio();
        Horario horario = edificio.getHorario();
        LocalDateTime ahora = ahora();
        Franja franja = franjaDelFiltro(filtro, horario, ahora);

        List<Piso> pisosDelEdificio = pisoRepository.findByEdificioIdOrderByNumeroDesc(edificio.getId());
        List<Espacio> espaciosDelEdificio = espacioRepository.findByPisoIdInOrderByCodigo(
                pisosDelEdificio.stream().map(Piso::getId).toList());
        LocalDateTime desde = inicioDelDia(franja.inicio());
        Map<Long, List<Ocupacion>> ocupadas = ocupaciones.porEspacio(
                espaciosDelEdificio.stream().map(Espacio::getId).toList(), desde, desde.plusDays(DIAS_BUSQUEDA + 1));

        Predicate<Espacio> cumple = cumpleFiltros(filtro);
        List<Espacio> espacios = espaciosDelEdificio.stream().filter(e -> e.getPiso().getId().equals(pisoId)).toList();
        LocalDateTime buscarDesde = franja.inicio().isAfter(ahora) ? franja.inicio() : ahora;
        // "Próximo libre" answers for the length the user asked for, not for any half hour.
        Duration largoBuscado = franja.duracion().compareTo(ReglasReserva.MAXIMA) > 0 ? ReglasReserva.MAXIMA : franja.duracion();

        List<EspacioEnPlano> enPlano = espacios.stream().map(e -> {
            List<Ocupacion> delEspacio = Ocupaciones.de(ocupadas, e.getId());
            return new EspacioEnPlano(
                    e.getId(), e.getCodigo(), e.getTipo(), e.getTipo().getEtiqueta(), e.getCapacidad(),
                    Boolean.TRUE.equals(e.getVentana()), e.getOrientacion(), tarifario.porHora(e.getTipo()),
                    Vistas.equipamientoCorto(e),
                    Vistas.posicion(e.getPosicion()),
                    motor.estado(delEspacio, franja),
                    motor.ocupacionEn(delEspacio, franja).map(o -> new FranjaDTO(o.inicio(), o.fin())).orElse(null),
                    FranjaDTO.de(motor.proximoHueco(delEspacio, buscarDesde, horario, largoBuscado, DIAS_BUSQUEDA)
                            .orElse(null)),
                    cumple.test(e));
        }).toList();

        List<EspacioEnPlano> filtrados = enPlano.stream().filter(EspacioEnPlano::cumpleFiltros).toList();
        ResumenPlano resumen = new ResumenPlano(
                filtrados.size(),
                contar(filtrados, EstadoEspacio.LIBRE),
                contar(filtrados, EstadoEspacio.OCUPADO),
                contar(filtrados, EstadoEspacio.BLOQUEADO));

        List<PisoLibres> otrosPisos = pisosDelEdificio.stream()
                .filter(p -> !p.getId().equals(pisoId))
                .map(p -> new PisoLibres(p.getId(), p.getNombre(), (int) espaciosDelEdificio.stream()
                        .filter(e -> e.getPiso().getId().equals(p.getId()) && cumple.test(e))
                        .filter(e -> motor.estado(Ocupaciones.de(ocupadas, e.getId()), franja) == EstadoEspacio.LIBRE)
                        .count()))
                .toList();

        Franja sugerencia = resumen.libres() > 0 ? null : motor.proximaFranjaConLibres(
                espacios.stream().filter(cumple).map(e -> Ocupaciones.de(ocupadas, e.getId())).toList(),
                franja.inicio().plus(MotorDisponibilidad.PASO), franja.duracion(), horario).orElse(null);

        List<ServicioEnPlano> servicios = servicioPisoRepository.findByPisoIdOrderByPosicionX(pisoId).stream()
                .map(s -> new ServicioEnPlano(s.getId(), s.getTipo(), s.getNombre(), Vistas.posicion(s.getPosicion())))
                .toList();
        List<AreaEnPlano> areas = areaPlanoRepository.findByPisoId(pisoId).stream()
                .map(a -> new AreaEnPlano(a.getId(), a.getTipo(), a.getEtiqueta(), Vistas.posicion(a.getPosicion())))
                .toList();

        return new PlanoPiso(
                new PisoInfo(piso.getId(), piso.getNumero(), piso.getNombre(), piso.getPlanoColumnas(),
                        piso.getPlanoFilas(), piso.getDisposicion().getEtiqueta(), piso.getMurosConVentana(),
                        Vistas.comodidades(piso, servicioPisoRepository.findByPisoIdOrderByPosicionX(pisoId))),
                Vistas.edificioRef(edificio),
                new Ref(edificio.getEmpresa().getId(), edificio.getEmpresa().getNombre()),
                FranjaDTO.de(franja),
                enPlano,
                servicios,
                areas,
                resumen,
                otrosPisos,
                reglas.condiciones(),
                FranjaDTO.de(sugerencia));
    }

    @Override
    public List<EspacioBreve> listarEspaciosDePiso(Long pisoId) {
        if (!pisoRepository.existsById(pisoId)) {
            throw new NotFoundException("Piso no encontrado");
        }
        return espacioRepository.findByPisoIdOrderByCodigo(pisoId).stream().map(Vistas::espacioBreve).toList();
    }

    // ---- Space detail and calendar ----------------------------------------------------------

    @Override
    public EspacioDetalle obtenerEspacio(Long espacioId) {
        Espacio e = espacioRepository.findConUbicacionById(espacioId)
                .orElseThrow(() -> new NotFoundException("Espacio no encontrado"));
        Piso piso = e.getPiso();
        Edificio edificio = piso.getEdificio();

        List<ServicioCercano> cercanos = servicioPisoRepository.findByPisoIdOrderByPosicionX(piso.getId()).stream()
                .map(s -> new ServicioCercano(s.getTipo(), s.getNombre(), e.getPosicion().metrosHasta(s.getPosicion())))
                .sorted(Comparator.comparingInt(ServicioCercano::metros))
                .toList();

        return new EspacioDetalle(
                e.getId(), e.getCodigo(), e.getTipo(), e.getTipo().getEtiqueta(), e.getCapacidad(),
                Boolean.TRUE.equals(e.getVentana()), e.getDescripcion(), e.getSuperficie(), e.getOrientacion(),
                e.getUbicacion(), e.getRuido(), e.getClimatizacion(), e.getEquipamiento(),
                new PisoRef(piso.getId(), piso.getNumero(), piso.getNombre()),
                Vistas.edificioRef(edificio),
                new Ref(edificio.getEmpresa().getId(), edificio.getEmpresa().getNombre()),
                cercanos,
                reglas.condiciones(),
                tarifario.porHora(e.getTipo()));
    }

    @Override
    public AgendaEspacio obtenerAgenda(Long espacioId, LocalDate desde, LocalDate hasta, String email, boolean esAdmin) {
        Espacio espacio = espacioRepository.findConUbicacionById(espacioId)
                .orElseThrow(() -> new NotFoundException("Espacio no encontrado"));
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) > MAXIMO_DIAS_AGENDA) {
            throw new BadRequestException("El rango de fechas no es válido");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        List<OcupacionCalendario> calendario = new ArrayList<>();
        for (Reserva r : reservaRepository.findEnRango(List.of(espacioId), inicio, fin, EstadoReserva.ACTIVOS)) {
            boolean propia = r.getUsuario().getEmail().equalsIgnoreCase(email);
            boolean conDetalle = propia || esAdmin;
            calendario.add(new OcupacionCalendario("RESERVA", r.getInicio(), r.getFin(), propia,
                    conDetalle ? r.getId() : null,
                    conDetalle ? r.getCodigo() : null,
                    conDetalle ? r.estadoEn(ahora()).name() : null,
                    esAdmin ? r.getUsuario().getNombre() : null,
                    null));
        }
        for (Bloqueo b : bloqueoRepository.findEnRango(List.of(espacioId), inicio, fin)) {
            calendario.add(new OcupacionCalendario("BLOQUEO", b.getInicio(), b.getFin(), false, null, null, null, null,
                    esAdmin ? b.getMotivo().getEtiqueta() + (b.getDetalle().isBlank() ? "" : " — " + b.getDetalle()) : null));
        }
        calendario.sort(Comparator.comparing(OcupacionCalendario::inicio));

        Edificio edificio = espacio.getPiso().getEdificio();
        return new AgendaEspacio(espacioId, edificio.getHoraApertura(), edificio.getHoraCierre(), desde, hasta, ahora(),
                calendario);
    }

    @Override
    public List<FranjaDTO> franjasLibres(Long espacioId, LocalDate fecha, int duracionMinutos, LocalTime cerca) {
        Espacio espacio = espacioRepository.findConUbicacionById(espacioId)
                .orElseThrow(() -> new NotFoundException("Espacio no encontrado"));
        Horario horario = espacio.getPiso().getEdificio().getHorario();
        Duration duracion = Duration.ofMinutes(Math.max(duracionMinutos, (int) ReglasReserva.MINIMA.toMinutes()));
        LocalDateTime referencia = fecha.atTime(cerca != null ? cerca : horario.apertura());
        List<Ocupacion> delEspacio = Ocupaciones.de(
                ocupaciones.porEspacio(List.of(espacioId), fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()), espacioId);
        return motor.huecosCercanos(delEspacio, fecha, horario, ahora(), duracion, referencia, 3).stream()
                .map(FranjaDTO::de)
                .toList();
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    private static LocalDateTime inicioDelDia(LocalDateTime t) {
        return t.toLocalDate().atStartOfDay();
    }

    /**
     * Day a building's "libres hoy" refers to: today while at least one minimum slot can still
     * be booked, tomorrow once it is too late.
     */
    private static LocalDate jornada(Horario horario, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        boolean quedaLugar = horario.abre(hoy)
                && !MotorDisponibilidad.redondearArriba(ahora).plus(ReglasReserva.MINIMA).isAfter(horario.cierreEn(hoy));
        return quedaLugar ? hoy : horario.proximoDiaHabil(hoy.plusDays(1));
    }

    private static boolean estaAbierto(Horario horario, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        return horario.abre(hoy) && !ahora.isBefore(horario.aperturaEn(hoy)) && ahora.plusMinutes(1).isBefore(horario.cierreEn(hoy));
    }

    /** "Libres ahora": the next half hour while open, otherwise the first half hour after opening. */
    private static Franja franjaAhora(Horario horario, LocalDateTime ahora) {
        if (estaAbierto(horario, ahora)) {
            LocalDateTime inicio = ahora.truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime fin = inicio.plus(ReglasReserva.MINIMA);
            LocalDateTime cierre = horario.cierreEn(ahora.toLocalDate());
            return new Franja(inicio, fin.isAfter(cierre) ? cierre : fin);
        }
        LocalDate hoy = ahora.toLocalDate();
        LocalDate dia = horario.proximoDiaHabil(horario.abre(hoy) && ahora.isBefore(horario.aperturaEn(hoy)) ? hoy : hoy.plusDays(1));
        return new Franja(horario.aperturaEn(dia), horario.aperturaEn(dia).plus(ReglasReserva.MINIMA));
    }

    private static Franja franjaDelFiltro(FiltroPlano filtro, Horario horario, LocalDateTime ahora) {
        if (filtro.fecha() != null && filtro.desde() != null && filtro.hasta() != null) {
            if (!filtro.hasta().isAfter(filtro.desde())) {
                throw new BadRequestException("El horario «hasta» tiene que ser posterior a «desde»");
            }
            return new Franja(filtro.fecha().atTime(filtro.desde()), filtro.fecha().atTime(filtro.hasta()));
        }
        // Next reasonable slot: two hours from the next :00 or :30, or the next opening.
        LocalDateTime inicio = MotorDisponibilidad.redondearArriba(ahora);
        LocalDate hoy = ahora.toLocalDate();
        if (inicio.isBefore(horario.aperturaEn(hoy))) {
            inicio = horario.aperturaEn(hoy);
        }
        if (!horario.abre(hoy) || inicio.plusHours(1).isAfter(horario.cierreEn(hoy))) {
            inicio = horario.aperturaEn(horario.proximoDiaHabil(hoy.plusDays(1)));
        }
        LocalDateTime cierre = horario.cierreEn(inicio.toLocalDate());
        LocalDateTime fin = inicio.plus(FRANJA_POR_DEFECTO);
        return new Franja(inicio, fin.isAfter(cierre) ? cierre : fin);
    }

    private static Predicate<Espacio> cumpleFiltros(FiltroPlano filtro) {
        return e -> (filtro.tipo() == null || e.getTipo() == filtro.tipo())
                && (filtro.capacidadMinima() == null || e.getCapacidad() >= filtro.capacidadMinima())
                && (!Boolean.TRUE.equals(filtro.ventana()) || Boolean.TRUE.equals(e.getVentana()));
    }

    private static int contar(List<EspacioEnPlano> espacios, EstadoEspacio estado) {
        return (int) espacios.stream().filter(e -> e.estado() == estado).count();
    }

    private Map<Long, EdificioResumen> resumirEdificios(List<Edificio> edificios, List<EspacioUbicacion> ubicaciones,
                                                       Map<Long, List<Ocupacion>> ocupadas, LocalDateTime ahora) {
        Map<Long, List<EspacioUbicacion>> porEdificio = ubicaciones.stream()
                .collect(Collectors.groupingBy(EspacioUbicacion::edificioId));
        return edificios.stream().collect(Collectors.toMap(Edificio::getId, edificio -> {
            List<EspacioUbicacion> espacios = porEdificio.getOrDefault(edificio.getId(), List.of());
            Horario horario = edificio.getHorario();
            LocalDate jornada = jornada(horario, ahora);
            int libres = (int) espacios.stream()
                    .filter(u -> motor.huecosDelDia(Ocupaciones.de(ocupadas, u.espacioId()), jornada, horario, ahora).stream()
                            .anyMatch(h -> h.duracion().compareTo(ReglasReserva.MINIMA) >= 0))
                    .count();
            int pisos = (int) espacios.stream().map(EspacioUbicacion::pisoId).distinct().count();
            return new EdificioResumen(edificio.getId(), edificio.getNombre(), edificio.getDireccion(),
                    edificio.getLocalidad(), edificio.getHoraApertura(), edificio.getHoraCierre(), edificio.getTono(),
                    pisos, espacios.size(), libres, jornada);
        }));
    }

    private static EmpresaResumen resumirEmpresa(Empresa empresa, List<Edificio> edificios, Map<Long, EdificioResumen> resumenes) {
        List<EdificioResumen> propios = edificios.stream().map(e -> resumenes.get(e.getId())).toList();
        return new EmpresaResumen(
                empresa.getId(),
                empresa.getNombre(),
                empresa.getRubro(),
                empresa.getTono(),
                propios.size(),
                propios.stream().map(EdificioResumen::localidad).distinct().toList(),
                propios.stream().mapToInt(EdificioResumen::espaciosLibres).sum(),
                propios.stream().map(EdificioResumen::jornada).distinct().sorted().toList());
    }
}
