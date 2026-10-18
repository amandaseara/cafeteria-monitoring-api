# Cafeteria API

## 1. Descripción
REST API para la gestion de productos y pedidos de una cafeteria, desarrollada con **Java 21 y Spring Boot**, contenerizada con **Docker** y preparada para ejecutarse en un cluster local de **Kubernetes (Minikube)**. Expone metricas de negocio mediante **Micrometer/Prometheus** y permite visualizarlas desde **Grafana**.

> **Alcance:** proyecto demostrativo. El despliegue incluido es local con H2 en memoria. Los manifiestos y procedimientos estan preparados pero el despliegue debe ejecutarse y verificarse en el entorno de quien revise el repositorio.

## Funcionalidades

- Crear, listar, consultar, actualizar y desactivar productos.
- Registrar pedidos con uno o varios productos y cantidades.
- Calcular totales a partir del precio vigente al registrar el pedido y conservar el precio unitario en cada item.
- Consultar el historial de pedidos.
- Exponer health checks y metricas Prometheus.
- Evaluar una alerta cuando un producto supera 5 pedidos en una ventana de 5 minutos.
- Activar un escenario de demostracion que genera pedidos automaticamente.

## Stack

- Backend: Java 21, Spring Boot 4.1.1, Spring Web MVC
- Persistencia: Spring Data JPA, H2 en memoria
- Observabilidad: Spring Boot Actuator, Micrometer, Prometheus
- Visualizacion: Grafana
- Contenedores y orquestacion: Docker, Kubernetes, Minikube
- Build: Maven Wrapper

## Ejecución local (sin contenedores)

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

- API: `http://localhost:8081`
- Health check: `http://localhost:8081/actuator/health`
- Metricas: `http://localhost:8081/actuator/prometheus`

Para activar los pedidos automaticos en ejecucion local:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

## Docker

Construir la imagen:

```powershell
docker build -t cafeteria-api:1.0 .
```

Ejecutar la API sin el perfil demo:

```powershell
docker run --rm --name cafeteria-api-local -p 8081:8081 cafeteria-api:1.0
```

Comprobar el health check desde otra terminal:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

## Despliegue en Kubernetes (Minikube)

**1. Iniciar el cluster**

```powershell
minikube start --driver=docker --cpus=2 --memory=4096
kubectl get nodes
```

**2. Construir y cargar la imagen local en Minikube**

```powershell
docker build -t cafeteria-api:1.0 .
minikube image load cafeteria-api:1.0
```

**3. Aplicar los manifiestos**

```powershell
kubectl apply -f k8s/api-minikube.yaml
kubectl apply -f k8s/prometheus.yaml
kubectl apply -f k8s/grafana.yaml
```

**4. Verificar los servicios**

```powershell
kubectl rollout status deployment/cafeteria-api --timeout=180s
kubectl rollout status deployment/cafeteria-prometheus --timeout=180s
kubectl rollout status deployment/cafeteria-grafana --timeout=180s
kubectl get pods
kubectl get services
```

**5. Abrir tres terminales PowerShell independientes**

```powershell
# Terminal 1: API
kubectl port-forward service/cafeteria-api 8081:8081
```

```powershell
# Terminal 2: Prometheus
kubectl port-forward service/cafeteria-prometheus 9090:9090
```

```powershell
# Terminal 3: Grafana
kubectl port-forward service/cafeteria-grafana 3000:3000
```

- API: http://localhost:8081/api/products
- Health: http://localhost:8081/actuator/health
- Prometheus: http://localhost:9090
- Alertas: http://localhost:9090/alerts
- Grafana: http://localhost:3000

En Minikube la API se ejecuta con `SPRING_PROFILES_ACTIVE=demo`: crea productos y genera los pedidos definidos en `demo-data.json` cada 15 segundos hasta agotar los casos.

## Endpoints de la API

 GET
 - `/api/products`: Lista productos activos
 - `/api/products/{id}`: Consulta producto activo
 - `/api/orders`: Lista pedidos
 - `/api/orders/{id}`: Consulta pedido individual
 - `/actuator/health`: Estado
 - `/actuator/prometheus`: Metricas para Prometheus
 POST
 - `/api/products`: Crea producto
 - `/api/orders`: Crea un pedido
 PUT
 - `/api/products/{id}`: Modifica nombre, descripcion y precio
 DELETE
 - `/api/products/{id}`: Desactiva producto

### Ejemplos con PowerShell

Crear producto:

```powershell
$baseUrl = 'http://localhost:8081'
$product = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/products" -ContentType 'application/json' -Body '{"name":"Latte","description":"Café con leche","price":180.00}'
$product
```

Actualizar precio:

```powershell
Invoke-RestMethod -Method Put -Uri "$baseUrl/api/products/$($product.id)" -ContentType 'application/json' -Body '{"name":"Latte","description":"Café con leche grande","price":200.00}'
```

Crear pedido de dos unidades:

```powershell
$payload = @{ items = @(@{ productId = $product.id; quantity = 2 }) } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/orders" -ContentType 'application/json' -Body $payload
```

Listar pedidos:

```powershell
Invoke-RestMethod "$baseUrl/api/orders"
```

Desactivar producto:

```powershell
Invoke-RestMethod -Method Delete -Uri "$baseUrl/api/products/$($product.id)"
```

## Metricas y alertas

Micrometer registra el contador `cafeteria.product.orders` con las etiquetas `product_id` y `product_name` exportado a Prometheus como:

```promql
cafeteria_product_orders_total
```

Pedidos por producto en los ultimos cinco minutos:

```promql
sum by (product_id, product_name) (
  increase(cafeteria_product_orders_total[5m])
)
```

La regla `HighProductOrderRate` se dispara cuando esa expresion supera 5:

```promql
sum by (product_id, product_name) (
  increase(cafeteria_product_orders_total[5m])
) > 5
```

Tiene `for: 0m` y severidad `warning`. Podes comprobar su estado en `http://localhost:9090/alerts` y usar la consulta:

```promql
ALERTS{alertname="HighProductOrderRate",alertstate="firing"}
```

## Grafana

1. Ingresar en `http://localhost:3000`.
2. Autenticarse con el usuario demo **`admin`** y contraseña demo **`cafeteria123`**.
3. Ir a **Connections → Data sources → Add data source → Prometheus**.
4. Configurar la URL **`http://cafeteria-prometheus:9090`**.
5. Seleccionar **Save & test** y crear un dashboard.

Consultas recomendadas:

**Pedidos acumulados**

```promql
sum by (product_name) (cafeteria_product_orders_total)
```

**Pedidos en cinco minutos**

```promql
sum by (product_name) (increase(cafeteria_product_orders_total[5m]))
```

**Cantidad de alertas activas**

```promql
count(ALERTS{alertname="HighProductOrderRate",alertstate="firing"}) or vector(0)
```

## Limpieza del entorno

```powershell
kubectl delete -f k8s/grafana.yaml
kubectl delete -f k8s/prometheus.yaml
kubectl delete -f k8s/api-minikube.yaml
minikube stop
```
