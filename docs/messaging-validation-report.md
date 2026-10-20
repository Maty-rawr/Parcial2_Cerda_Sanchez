# Informe de validación SNS/SQS

**Fecha:** 2026-10-20  
**Alcance:** auditoría del código local, pruebas que no escriben en AWS y estado de bloqueos externos.  
**Conclusión:** **parcial; no equivale a aprobación completa de la evaluación.** El perfil AWS Academy autentica en `us-east-1`, pero las consultas de inventario están denegadas explícitamente. No se ejecutó ninguna escritura en AWS.

## Estados

- **APROBADO:** prueba ejecutada y resultado correcto, dentro del alcance indicado.
- **FALLIDO:** prueba ejecutada y resultado incorrecto.
- **BLOQUEADO:** no se pudo ejecutar por permisos o limitaciones externas.
- **NO EJECUTADO:** falta ejecutar la prueba.

Una prueba unitaria con mocks verifica código, no prueba que existan o funcionen recursos AWS reales.

## Matriz de cumplimiento y evidencias

| Criterio de la pauta | Estado | Resultado y evidencia |
|---|---|---|
| Encargo: centralizar nombres de topic, colas y consumidores | **APROBADO local / BLOQUEADO AWS** | `MessagingProperties` enlaza ARN/URLs y parámetros desde `application.properties`; `MessagingNames` centraliza dominios/tipos. Falta cotejarlos con AWS. |
| Encargo: declarar topic, tres colas, tres DLQ, redrive, suscripciones y monitoreo | **APROBADO** | La plantilla aislada se parseó localmente y pasó comprobaciones estructurales de recursos, filtros, políticas, parámetros y ausencia de RDS/otros servicios; ver [cloudformation-template-validation.md](./evidencias/cloudformation-template-validation.md). No equivale a `validate-template` ni a despliegue. |
| Encargo: separar configuración AWS y lógica de negocio | **APROBADO** | Inspección de beans/publicador y tests de `SnsEventPublisher`; no se implementó una outbox durable. |
| Encargo: consumidores organizados por dominio | **APROBADO local / BLOQUEADO AWS** | Se separaron Orders/Inventory/Notifications en clases con perfil y polling independientes que delegan al servicio común. Se aplicó Plan B con SDK/`@Scheduled`, sin `@SqsListener`; no se verificó una versión Spring Cloud AWS compatible con Spring Boot 4.1.1. Falta probar los flujos reales. |
| Encargo: confirmación explícita, reintentos, DLQ, idempotencia y logs | **APROBADO** | Tests unitarios simulados verifican procesar antes de ACK, error recuperable sin borrar, envío a DLQ antes de ACK, fallo de envío a DLQ sin borrar y ausencia de log de éxito si falla el ACK. Persistencia e idempotencia están cubiertas por tests unitarios existentes. Redrive y persistencia real quedan sin prueba. |
| Encargo: API REST administradora y autorización | **NO EJECUTADO** | El controlador delega al servicio y tiene `@PreAuthorize("hasRole('ADMIN')")`; faltan pruebas HTTP, Security y roles. |
| Encargo: administración encapsulada en servicio | **APROBADO** | Tests con clientes simulados verifican creación/baja/listado de topic y suscripción, creación/baja/estado de cola, pausa/reanudación, redrive y protección de purga. El test también verifica que añadir permiso SNS conserva una regla previa de la política SQS. No es efecto real en AWS. |
| Presentación: topic/colas/suscripciones operativas y parámetros | **BLOQUEADO** | STS autentica y región es `us-east-1`; `sns:ListTopics` tiene denegación explícita, así que no se pudo consultar SNS. |
| Presentación: dos instancias sobre la misma cola, parar una y demostrar continuidad | **BLOQUEADO** | Requiere configuración real de AWS/RDS y credenciales temporales; no se iniciaron workers contra recursos remotos. Se documentó arranque de dos procesos en el README. |
| Presentación: tres colas con funciones diferenciadas | **BLOQUEADO** | La topología está declarada; no se comprobó el flujo en recursos AWS. El consumidor de notifications registra el evento en la BD y logs, pero no se observó un envío externo de notificación. |
| Presentación: cada cola con DLQ y flujo real | **BLOQUEADO** | `sqs:ListQueues` tiene denegación explícita; no se pudieron leer atributos/redrive ni probar llegada real a las DLQ. |
| Presentación: fanout y filtros de suscripción | **BLOQUEADO** | `sns:ListTopics` tiene denegación explícita; no se pudieron leer suscripciones/filtros ni publicar mensajes contra AWS. |
| Presentación: productores y consumidores extremo a extremo | **BLOQUEADO** | No se usaron SNS/SQS reales; `MESSAGING_ENABLED` está desactivado por defecto y no se cargó configuración AWS en este trabajo. |
| Presentación: logs asociados a mensajes reales de DLQ | **BLOQUEADO** | Los logs de unit tests son simulados; falta correlacionarlos con mensajes AWS reales. |
| Presentación: administración REST con efecto real AWS | **BLOQUEADO** | Operaciones de servicio verificadas con clientes simulados; no se hicieron mutaciones AWS. |
| Panel de mensajería en frontend admin | **APROBADO local / BLOQUEADO AWS** | Pestaña SNS/SQS con conteos aproximados de las tres colas/DLQ, hasta 50 eventos procesados sin payload, estado y pausa/reanudación. El endpoint requiere `ROLE_ADMIN`; solo consulta atributos SQS y la tabla de eventos. Build/lint pasa; falta conectar y probar contra AWS real. |
| Presentación: métricas y alarma CloudWatch | **BLOQUEADO** | `cloudwatch:DescribeAlarms` tiene denegación explícita; el estado y las métricas reales no se consultaron. |
| Presentación: retención, limpieza y purga justificadas | **NO EJECUTADO** | Plantilla declara 4 días para colas, 14 días para DLQ y visibility timeout de 60 s; falta verificar valores reales y justificar su elección operativa. No se purgó nada. |
| Repositorio: secretos y estado Git | **BLOQUEADO** | Un escaneo de archivos visibles fuera de `node_modules` no halló patrones de credenciales. El `.env` local del frontend admin no se leyó y los archivos ocultos no formaron parte de ese escaneo; el nombre está cubierto por `.gitignore`. Sin `.git` ni ejecutable Git, no se puede confirmar si hay archivos trackeados, cambios previos o historial. |
| Inventario de CloudFormation y recursos AWS | **BLOQUEADO** | `cloudformation:ListStacks` tiene denegación explícita; no se conoce el estado real de los stacks. Ver [evidencia AWS](./evidencias/aws-readonly-inventory.md). |
| Pruebas completas del backend | **APROBADO** | `.\mvnw.cmd clean test`: **26 pruebas, 0 fallos, 0 errores, 2 omitidas**; BUILD SUCCESS. Las 2 omitidas pertenecen a Testcontainers porque Docker no está disponible. |
| Build y lint de ambos frontends | **APROBADO** | Build/lint de ambos frontends pasaron en validaciones previas; después de añadir el panel, `npm.cmd run build` y `npm.cmd run lint` volvieron a pasar en el admin. No hay scripts `test` en sus `package.json`. |

## Cambios locales realizados

1. Se movieron los logs de “procesado/duplicado” después de la confirmación de `DeleteMessage`; un ACK fallido ahora se registra como reintento y no como éxito.
2. El envío manual a DLQ se registra como exitoso solo después de que `SendMessage` retorne; si el envío falla, no se confirma el mensaje original.
3. Se dejó que `ReceiveMessage` use el long polling configurado en SQS (20 s), en vez de sobrescribirlo a 2 s.
4. Se alineó el `maxReceiveCount` del consumidor, el administrador y el nuevo parámetro CloudFormation `MaxReceiveCount` (predeterminado 5).
5. La operación de suscripción preserva políticas SQS previas al añadir el permiso de SNS limitado por `aws:SourceArn`.
6. La plantilla combinada `aws-skates-platform.yaml` tiene una DLQ de entrega SNS para Orders; su política queda limitada al ARN de `OrdersSubscription`. La plantilla aislada no configura SNS delivery redrive, pero configura SQS processing redrive para las tres colas principales.
7. Se añadieron pruebas unitarias de consumidor, publicador SNS, administrador y control de pausa; `.env` y `.env.*` se agregaron al `.gitignore` del backend.
8. El README documenta variables, perfiles, clasificación de errores y los puertos sugeridos `8081`/`8082`/`8083`/`8084`/`8085`. Se habilitó el perfil `admin` para servir también el CRUD de productos usado por el panel y publicar eventos de inventario; esto aún requiere validación estática/de integración local.
9. Se centralizó la configuración en `MessagingProperties` y se separaron los consumidores por dominio mediante el Plan B de polling con SDK.
10. Se añadió `GET /api/admin/messaging/overview` y una pestaña de mensajería al panel admin, con métricas, los últimos 50 eventos procesados sin payload y pausa/reanudación; consulta atributos SQS sin recibir mensajes.

## Evidencia local

Los resultados automatizados del backend están en `docs/evidencias/maven/`. Los logs de build/lint de frontend y el resumen de comandos quedan en `docs/evidencias/`. Estos artefactos no prueban el estado de AWS.

## Bloqueos y siguientes pasos

1. AWS CLI y perfil `academy` están configurados; STS autentica y región es `us-east-1`. Sin embargo, la política `voc-cancel-cred` deniega explícitamente `cloudformation:ListStacks`, `sns:ListTopics`, `sqs:ListQueues` y `cloudwatch:DescribeAlarms`. El inventario sanitizado está en [aws-readonly-inventory.md](./evidencias/aws-readonly-inventory.md).
2. Solicitar al docente/administrador de AWS Academy que revise la política/permisos o facilite evidencia sanitizada de solo lectura. No se intentó eludir la denegación.
3. Restaurar acceso a Git en el checkout correcto para registrar el estado local antes de cambios posteriores.
4. Con lectura autorizada, inventariar stacks, SNS, SQS, filtros, políticas, CloudWatch e IAM. No se puede suponer que los recursos de la plantilla existan.
5. Solo después del inventario, preparar el plan exacto de escritura AWS y esperar aprobación antes de ejecutar cambios. No se desplegará la plantilla actual completa porque incluye RDS. Hay una plantilla separada lista para revisión en `Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-messaging.yaml`.
6. Ejecutar pruebas de integración reales, concurrencia de dos workers y evidencias de consola cuando el laboratorio y RDS estén disponibles y los permisos hayan sido habilitados/aprobados.
7. Para el procedimiento paso a paso, consultar [aws-academy-manual-steps.md](./aws-academy-manual-steps.md).

## Plan de cambios AWS

**Propuesto, no ejecutado ni aprobado.** Después de que AWS permita inspeccionar y confirmar que los recursos faltan, el posible stack separado crearía: un topic SNS; tres colas principales; tres DLQ; tres QueuePolicy limitadas al topic; tres suscripciones con filtros por `eventType` y raw delivery; tres alarmas de DLQ y un dashboard. No crearía, cambiaría ni eliminaría RDS, EC2, VPC, Cognito, API Gateway, roles IAM ni recursos existentes. Antes de cualquier escritura se debe revisar el inventario, change set, cuotas/costos y aprobación explícita. Si los recursos ya existen, se conservan y se propone corregirlos puntualmente, no duplicarlos.

La configuración propuesta conserva los valores existentes de referencia: 5 recepciones, 4 días para colas, 14 días para DLQ, timeout de visibilidad de 60 segundos y long polling de 20 segundos. No son valores verificados en AWS ni recomendación final de negocio; revisar y justificar con el equipo.

Justificación preliminar documentada en la guía manual: 4 días absorben interrupciones breves; 14 días permiten investigar y decidir un redrive; 60 segundos deben exceder el tiempo típico de procesamiento; long polling de 20 segundos reduce recepciones vacías. Validar estos supuestos con la latencia medida y los SLA del caso antes de presentarlos como decisión final.

## Validación estructural de la plantilla nueva

Se parseó la plantilla con los tags intrínsecos CloudFormation y se comprobó localmente el número/tipo de recursos, las tres políticas de redrive, los filtros exactos de suscripción, `RawMessageDelivery`, las políticas SQS limitadas al topic y la ausencia de recursos ajenos a la mensajería. Resultado **APROBADO local**; la validación remota de AWS y change set quedan **BLOQUEADOS** por el explicit deny. Ver [cloudformation-template-validation.md](./evidencias/cloudformation-template-validation.md).
