package com.daii.backdaii.controller;

import com.daii.backdaii.domain.enums.Permiso;
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
import com.daii.backdaii.service.CatalogoService;
import com.daii.backdaii.service.CatalogoService.FiltroPlano;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Presentation layer of the catalog component: the empresa > edificio > piso > espacio
 * navigation, with availability already computed for the requested slot.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ESPACIOS_VER')")
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/empresas")
    public ResponseEntity<List<EmpresaResumen>> listarEmpresas() {
        return ResponseEntity.ok(catalogoService.listarEmpresas());
    }

    @GetMapping("/empresas/{id}")
    public ResponseEntity<EmpresaDetalle> obtenerEmpresa(@PathVariable Long id) {
        return ResponseEntity.ok(catalogoService.obtenerEmpresa(id));
    }

    @GetMapping("/edificios")
    public ResponseEntity<List<EdificioConPisos>> listarEdificios() {
        return ResponseEntity.ok(catalogoService.listarEdificiosConPisos());
    }

    @GetMapping("/edificios/{id}")
    public ResponseEntity<EdificioDetalle> obtenerEdificio(@PathVariable Long id) {
        return ResponseEntity.ok(catalogoService.obtenerEdificio(id));
    }

    @GetMapping("/pisos/{id}/plano")
    public ResponseEntity<PlanoPiso> obtenerPlano(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime desde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime hasta,
            @RequestParam(required = false) TipoEspacio tipo,
            @RequestParam(required = false) Integer capacidadMinima,
            @RequestParam(required = false) Boolean ventana) {
        return ResponseEntity.ok(catalogoService.obtenerPlano(id,
                new FiltroPlano(fecha, desde, hasta, tipo, capacidadMinima, ventana)));
    }

    @GetMapping("/pisos/{id}/espacios")
    public ResponseEntity<List<EspacioBreve>> listarEspacios(@PathVariable Long id) {
        return ResponseEntity.ok(catalogoService.listarEspaciosDePiso(id));
    }

    @GetMapping("/espacios/{id}")
    public ResponseEntity<EspacioDetalle> obtenerEspacio(@PathVariable Long id) {
        return ResponseEntity.ok(catalogoService.obtenerEspacio(id));
    }

    @GetMapping("/espacios/{id}/agenda")
    public ResponseEntity<AgendaEspacio> obtenerAgenda(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal UserDetails user) {
        boolean esAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Permiso.RESERVAS_VER_TODAS.name()));
        return ResponseEntity.ok(catalogoService.obtenerAgenda(id, desde, hasta, user.getUsername(), esAdmin));
    }

    @GetMapping("/espacios/{id}/franjas-libres")
    public ResponseEntity<List<FranjaDTO>> franjasLibres(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "60") int duracion,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime cerca) {
        return ResponseEntity.ok(catalogoService.franjasLibres(id, fecha, duracion, cerca));
    }
}
