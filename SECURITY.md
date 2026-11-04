# Seguridad — Cafeteria API

## 1. Descripción

Este documento describe los controles de seguridad implementados en Cafeteria API, una aplicación desarrollada con Java 21 y Spring Boot, contenerizada con Docker y desplegada localmente en Kubernetes mediante Minikube.

Se realizaron análisis de código, dependencias, imagen Docker, manifiestos Kubernetes y comportamiento en tiempo de ejecución.

> **Alcance:** proyecto demostrativo ejecutado en un entorno local.

## 2. Seguridad del código

### npm audit y OWASP Dependency-Check

No se ejecutó `npm audit` porque la aplicación utiliza Maven para gestionar sus dependencias Java y no cuenta con dependencias administradas mediante npm.

Se utilizó **OWASP Dependency-Check** para identificar vulnerabilidades conocidas en las dependencias del proyecto.

| Análisis | Inicial | Final |
|---|---:|---:|
| Dependencias vulnerables | 1 | 0 |
| Vulnerabilidades identificadas | 11 | 0 |

El análisis final examinó 99 dependencias. Las vulnerabilidades inicialmente detectadas fueron resueltas mediante la actualización de dependencias.

Evidencia:
- Inicial: `reports/initial/dependency-check-report.html`
- Final: `reports/final/dependency-check-final.html`

### Semgrep

Se ejecutó un análisis estático del código Java utilizando Semgrep.

- Reglas ejecutadas: 60.
- Archivos Java analizados: 14.
- Hallazgos: 0.

No se identificaron alertas que requirieran corrección. El análisis se limita a las reglas utilizadas; dos archivos quedaron excluidos por tamaño o configuración de exclusiones.

**Evidencia:** 
- Inicial:`reports/initial/semgrep.txt` y `reports/initial/dependency-check-report.html`.
- Final: `reports/final/semgrep-final.txt` y `reports/final/dependency-check-final.html`

## 3. Seguridad de la imagen Docker

### Trivy

Se analizaron la imagen inicial `cafeteria-api:1.0` y la versión corregida `cafeteria-api:1.1`.

| Severidad | Inicial | Final |
|---|---:|---:|
| CRITICAL | 4 | 0 |
| HIGH | 5 | 7 |
| MEDIUM | 55 | 49 |
| LOW | 4 | 20 |
| UNKNOWN | 0 | 1 |
| **Total** | **68** | **77** |

En la imagen final Trivy no identificó vulnerabilidades en `app/app.jar`. Los 77 hallazgos restantes corresponden a paquetes del sistema operativo Debian 13.7 utilizado por la imagen base.

### Justificación de vulnerabilidades pendientes

Las siete vulnerabilidades HIGH corresponden a:

| Componente | CVE | Cantidad |
|---|---|---:|
| `libexpat1` | CVE-2026-113351, CVE-2026-117610, CVE-2026-118370 | 3 |
| `libuuid1` / util-linux | CVE-2026-76642, CVE-2026-78408, CVE-2026-78409, CVE-2026-78410 | 4 |

Se decidió documentar y mantener estos hallazgos para el alcance del práctico. Pertenecen a bibliotecas del sistema operativo, y el reporte no indica versiones corregidas disponibles para los paquetes afectados.

El riesgo no se considera eliminado. Se mitiga parcialmente mediante la ejecución como usuario no root, filesystem de solo lectura, eliminación de capabilities y restricciones de recursos en Kubernetes.

**Evidencia:** 
- Inicial: `reports/final/trivyreport.txt`.
- Final: `reports/final/trivy-final.txt`.

## 4. Seguridad en Kubernetes

### Kyverno

Se implementaron tres políticas de admisión para los Pods del clúster:

1. **Prohibir imágenes `latest`:** exige utilizar etiquetas de versión explícitas.
2. **Exigir recursos:** requiere CPU y memoria en `requests` y `limits`.
3. **Impedir ejecución como root:** exige contenedores ejecutados como usuarios no privilegiados.

### Validación de políticas

Durante la instalación inicial de Falco, Kyverno rechazó el DaemonSet por incumplir las políticas `disallow-root` y `require-resources`.

Este rechazo permitió comprobar que las restricciones estaban activas y bloqueaban recursos no conformes.

Posteriormente se configuraron los recursos faltantes y una excepción limitada al namespace `falco`, debido a los privilegios necesarios para monitorizar eventos del kernel.

La excepción no se aplica a los contenedores de la API, Prometheus ni Grafana.

**Evidencia:**
- Configuración: `cafeteria-api/security/kyverno/`
- Manifiesto de Falco: `reports/falco-rendered.yaml`
- Validación: rechazo registrado en la salida de Helm durante el primer intento de instalación de Falco.


### KubeLinter

Se analizaron los manifiestos Kubernetes utilizando KubeLinter.

| Resultado | Inicial | Final |
|---|---:|---:|
| Hallazgos | 11 | 0 |

Los problemas identificados inicialmente fueron corregidos mediante la configuración de usuarios no root, filesystems de solo lectura, versiones específicas de imágenes y recursos de CPU y memoria.

**Evidencia:** 
- Inicial: `reports/initial/kubelinter.txt`
- Final: `reports/final/kubelinter-final.txt`

## 5. Seguridad en tiempo de ejecución

### Falco

Se instaló Falco 0.45.2 en Minikube mediante Helm.

Se verificó el funcionamiento del DaemonSet con un Pod disponible y sus dos contenedores en estado `Running`.

### Simulación de actividad sospechosa

Se creó un Pod temporal `falco-test` configurado con usuario no root, permisos restringidos y filesystem de solo lectura.

Se ejecutó una shell interactiva dentro del contenedor:

```powershell
kubectl exec -it falco-test -- /bin/sh
```

### Detección

Falco registró correctamente la ejecución de la shell mediante el siguiente evento:

```text
Notice A shell was spawned in a container with an attached terminal
```

El evento identificó el proceso `/bin/sh`, el UID `10001`, el Pod `falco-test`, el namespace `default` y la imagen `busybox:1.36.1`.

La prueba permitió validar la detección de actividad sospechosa durante la ejecución de contenedores.

**Evidencia:** `reports/falco-event.log`.

## 6. Conclución

El proyecto demuestra la aplicación de controles de seguridad en distintas etapas del ciclo de vida de una aplicación contenerizada, desde el análisis de código hasta la monitorización en tiempo de ejecución.

**Riesgo:** permanecen siete vulnerabilidades HIGH y otros hallazgos de menor severidad asociados a la imagen base Debian. Estos riesgos quedan documentados y requerirían una nueva evaluación antes de utilizar la aplicación en producción.