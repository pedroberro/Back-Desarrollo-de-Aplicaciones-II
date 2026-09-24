package com.daii.backdaii.service;

import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.AgendaEspacio;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EdificioConPisos;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EdificioDetalle;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EmpresaDetalle;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EmpresaResumen;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EspacioBreve;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EspacioDetalle;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.FranjaDTO;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.PlanoPiso;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Read side of the catalog: the navigation hierarchy and the availability painted on it. */
public interface CatalogoService {

    /** Optional filters of the plan. Without fecha/desde/hasta the next reasonable slot is used. */
    record FiltroPlano(LocalDate fecha, LocalTime desde, LocalTime hasta, TipoEspacio tipo,
                       Integer capacidadMinima, Boolean ventana) {
    }

    List<EmpresaResumen> listarEmpresas();

    EmpresaDetalle obtenerEmpresa(Long empresaId);

    EdificioDetalle obtenerEdificio(Long edificioId);

    List<EdificioConPisos> listarEdificiosConPisos();

    PlanoPiso obtenerPlano(Long pisoId, FiltroPlano filtro);

    List<EspacioBreve> listarEspaciosDePiso(Long pisoId);

    EspacioDetalle obtenerEspacio(Long espacioId);

    /** Occupations of one space between two dates. Administrators see who booked. */
    AgendaEspacio obtenerAgenda(Long espacioId, LocalDate desde, LocalDate hasta, String email, boolean esAdmin);

    /** Free ranges of the day long enough for the duration, closest to the wanted start first. */
    List<FranjaDTO> franjasLibres(Long espacioId, LocalDate fecha, int duracionMinutos, LocalTime cerca);
}
