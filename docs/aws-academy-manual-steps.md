# Guía manual para continuar en AWS Academy

**Región observada en el perfil local:** `us-east-1`. Compruébala en la consola antes de actuar.  
**Estado actual:** el perfil AWS autentica, pero una denegación explícita de la política `voc-cancel-cred` bloqueó cuatro consultas de solo lectura. No se hicieron cambios a AWS.  
**Regla de seguridad:** no despliegues ni crees recursos hasta completar el inventario, revisar duplicados/costos y obtener autorización del estudiante/responsable. No compartas Access Key, Secret Key ni Session Token.

## 1. Resolver la denegación de permisos

1. Inicia sesión en AWS Academy y abre el Learner Lab correcto; comprueba que esté iniciado.
2. En AWS Console, selecciona la misma región que muestra `aws configure get region --profile academy` (actualmente se observó `us-east-1`).
3. Solicita al docente/administrador que revise la denegación explícita asociada a `voc-cancel-cred`. Una denegación explícita prevalece sobre permisos Allow: no se corrige cambiando comandos, perfiles o región.
4. Pide acceso mínimo y temporal para estas acciones de lectura, o solicita al administrador que ejecute las consultas y proporcione capturas/exportaciones sanitizadas:
   - CloudFormation: `cloudformation:ListStacks`, `cloudformation:DescribeStacks`, `cloudformation:ListStackResources`, `cloudformation:DescribeStackEvents`.
   - SNS: `sns:ListTopics`, `sns:GetTopicAttributes`, `sns:ListSubscriptionsByTopic`, `sns:GetSubscriptionAttributes`.
   - SQS: `sqs:ListQueues`, `sqs:GetQueueAttributes`, `sqs:GetQueueUrl`.
   - CloudWatch: `cloudwatch:DescribeAlarms`, `cloudwatch:ListDashboards`, `cloudwatch:GetDashboard`, `cloudwatch:ListMetrics`, `cloudwatch:GetMetricData`.
   - Solo si el administrador autoriza inspeccionar permisos del backend: lectura acotada del rol existente, por ejemplo `iam:GetRole`, `iam:ListAttachedRolePolicies`, `iam:ListRolePolicies` e `iam:GetRolePolicy`.
   - Solo para validar localmente contra el servicio AWS (sin cambios): `cloudformation:ValidateTemplate`, si está permitido.
   
   Esta lista no solicita `Create*`, `Update*`, `Delete*`, `PurgeQueue` ni permisos administrativos generales. Una denegación explícita aún debe ser removida por el administrador de la política; no intentes cambiar/evadir IAM ni uses otra identidad.
5. Si el administrador habilita lectura temporal, vuelve a ejecutar los comandos de la sección 2. No ejecutes comandos de creación, actualización, borrado, publicación, consumo o purga en esta etapa.

Mensaje sugerido al docente:

> Mi perfil local `academy` autentica correctamente en `us-east-1`, pero el Learner Lab devuelve una denegación explícita de la política `voc-cancel-cred` para `cloudformation:ListStacks`, `sns:ListTopics`, `sqs:ListQueues` y `cloudwatch:DescribeAlarms`. Necesito verificar los recursos existentes para la Evaluación Parcial 2. ¿Puede revisar el estado/permisos del laboratorio o facilitar una exportación/capturas de solo lectura?

## 2. Inspección AWS de solo lectura

Usa PowerShell en el equipo donde ya configuraste el perfil. No compartas la salida completa si contiene identificadores de cuenta, ARNs personales, nombres de usuario o información del laboratorio; sanitízala antes.

```powershell
$Region = aws configure get region --profile academy
aws sts get-caller-identity --profile academy

aws cloudformation list-stacks --profile academy --region $Region `
  --query 'StackSummaries[].{Name:StackName,Status:StackStatus,Updated:LastUpdatedTime}' --output table

aws sns list-topics --profile academy --region $Region --output table
aws sqs list-queues --profile academy --region $Region --output table
aws cloudwatch describe-alarms --profile academy --region $Region `
  --query 'MetricAlarms[].{Name:AlarmName,State:StateValue,Metric:MetricName}' --output table
aws cloudwatch list-dashboards --profile academy --region $Region --output table
```

Para cada stack existente, primero lee los recursos y outputs:

```powershell
aws cloudformation list-stack-resources --profile academy --region $Region `
  --stack-name '<NOMBRE-STACK>' --output table
aws cloudformation describe-stacks --profile academy --region $Region `
  --stack-name '<NOMBRE-STACK>' --query 'Stacks[0].Outputs' --output table
```

Para cada cola, inspecciona atributos sin recibir mensajes:

```powershell
aws sqs get-queue-attributes --profile academy --region $Region `
  --queue-url '<URL-COLA>' --attribute-names All --output json
```

Registra `QueueArn`, `RedrivePolicy`, `RedriveAllowPolicy`, `Policy`, retención, visibilidad, long polling y contadores. No uses `receive-message`, `purge-queue` ni operaciones que cambien atributos para la auditoría.

Para el topic existente:

```powershell
aws sns list-subscriptions-by-topic --profile academy --region $Region `
  --topic-arn '<ARN-TOPIC>' --output table
aws sns get-subscription-attributes --profile academy --region $Region `
  --subscription-arn '<ARN-SUSCRIPCION>' --output json
```

Para CloudFormation, confirma también el stack que administra RDS y su estado. **No actualices `aws-skates-platform.yaml` en un stack en uso**: mezcla RDS con mensajería.

## 3. Decidir si se necesitan recursos nuevos

1. Compara el inventario con los recursos esperados: un topic, queues Orders/Inventory/Notifications, sus tres DLQ, suscripciones/filtros y alarmas/dashboard.
2. Si ya existen y están correctos, consérvalos; no dupliques recursos con nombres equivalentes. Documenta parámetros y outputs reales.
3. Si existe un stack que ya gestiona la mensajería, modifica ese stack solo mediante un plan/change set revisado y aprobado.
4. Si falta solo una parte de la topología o los nombres chocan con recursos existentes, **detente**: no lances la plantilla completa; prepara un plan puntual para adoptar/importar o corregir recursos sin reemplazos.
5. Si faltan todos los recursos de mensajería, confirma primero con el docente/estudiante que está permitido crearlos, el presupuesto/cuotas del Learner Lab y la región/nombres. Luego prepara un plan explícito de recursos a crear/modificar/conservar. No ejecutes el plan sin aprobación.
6. La plantilla separada [aws-skates-messaging.yaml](../Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-messaging.yaml) crea exclusivamente SNS, SQS y CloudWatch; **no crea RDS, EC2, VPC, Cognito ni API Gateway**. Su uso previsto es solo después de confirmar que no se duplican recursos y de obtener aprobación.

## 4. Despliegue manual de plantilla separada (solo tras aprobación)

1. En CloudFormation, confirma nuevamente la región y el inventario.
2. Elige **Create stack → With new resources (standard)** y carga `Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-messaging.yaml`.
3. Usa un nombre nuevo de stack acordado con el responsable, por ejemplo `skates-messaging-dev`. No elijas el stack de RDS y no intentes importar/adoptar recursos existentes sin un procedimiento aprobado.
4. Revisa parámetros:
   - `EnvironmentName`: prefijo de entorno sin colisionar con recursos actuales.
   - `MaxReceiveCount`: `5`, igual a `SQS_MAX_RECEIVE_COUNT`.
   - `QueueRetentionSeconds`: `345600` (4 días).
   - `DeadLetterRetentionSeconds`: `1209600` (14 días).
   - `QueueVisibilityTimeoutSeconds`: `60`.
   - `LongPollingSeconds`: `20`.
5. Revisa todas las acciones y recursos antes de enviar. Si CloudFormation advierte reemplazo, borrado, nombres duplicados o cambios ajenos a SNS/SQS/CloudWatch, cancela y consulta al responsable.
6. Inspecciona el **change set** y costos/cuotas; ejecútalo únicamente tras aprobación explícita. No habilites capacidades IAM: esta plantilla no crea roles ni políticas IAM de ejecución.
7. Espera el estado `CREATE_COMPLETE` y registra los outputs. Si falla, conserva los eventos de CloudFormation y no borres el stack ni los recursos automáticamente.
8. Configura el backend con outputs reales, `AWS_REGION`, `MESSAGING_ENABLED=true`, `SNS_TOPIC_ARN` y URLs de las seis colas. La identidad IAM del backend debe tener permisos mínimos para publicar/consumir. No uses credenciales embebidas en código ni en capturas.

Justificación operativa inicial para presentar (validar con el equipo antes de fijarla): cuatro días para la cola principal permiten absorber indisponibilidades breves y recuperar backlog; catorce días para la DLQ dan una ventana amplia para investigar y decidir redrive; 60 segundos de visibilidad deben superar el tiempo normal de procesamiento para evitar que otro worker reciba prematuramente el mismo mensaje; 20 segundos de long polling reducen respuestas vacías y llamadas innecesarias. Si la duración observada de procesamiento se acerca a 60 segundos o los incidentes se investigan fuera de dos semanas, revisa esos valores.

## 5. Verificar topología y flujo, solo con autorización

1. Confirma SNS→SQS, políticas de cola restringidas a `aws:SourceArn` del topic, filtros `eventType` y `RawMessageDelivery=true`.
2. En un topic/colas de prueba autorizados, publica un evento de pago con `eventType=PAYMENT_COMPLETED`: debe llegar a Orders y Notifications, no Inventory.
3. Publica `INVENTORY_UPDATED`: debe llegar a Inventory y Notifications, no Orders.
4. Comprueba el formato raw JSON que espera `QueueMessagePollingService`; no envíes eventos de prueba a colas de producción.
5. Valida reintento y DLQ solo con un mensaje de prueba y backend controlado. Para una prueba destructiva o de error intencional, acuerda antes el recurso y el procedimiento. No purgues DLQ reales.
6. Para disponibilidad, ejecuta dos workers de prueba con la misma cola, detén uno mientras quedan mensajes y demuestra continuidad/idempotencia. La pausa se comparte mediante la BD; los workers deben usar la misma base.
7. Revisa en CloudWatch mensajes visibles, no visibles, edad del más antiguo, contadores de DLQ y estado de alarmas. Documenta acciones ante una alarma.

Una alarma de DLQ debe iniciar investigación del evento/logs, causa y consumidor; no purgues para silenciarla. Redrive solo después de corregir la causa y verificar idempotencia. La retención elimina mensajes al vencer el periodo configurado; la purga manual es inmediata y destructiva, requiere autorización explícita y evidencia archivada.

## 6. Restricciones y costo

- No ejecutes `cloudformation deploy`, `create-stack`, `update-stack`, `delete-stack`, `sns create-topic`, `sqs create-queue`, `purge-queue`, `delete-queue`, `delete-topic`, `start-message-move-task` ni publicación real hasta tener inventario, plan y aprobación.
- Una denegación explícita requiere al administrador del laboratorio; no cambies de perfil para evitarla.
- Recursos de AWS y tráfico pueden generar costos/cuotas. Borra recursos de prueba solo con autorización expresa y después de verificar que no contienen datos necesarios.
- La plantilla actual combinada con RDS no debe usarse para resolver faltantes de mensajería.

## 7. Recorrido práctico para terminar en AWS

Sigue este orden cuando tengas acceso autorizado a las acciones necesarias. Una denegación en la consola también es una denegación IAM: detente y pide al docente/administrador que la resuelva; no la evadas cambiando de perfil, cuenta o región.

### A. Antes de crear recursos

1. Inicia el Learner Lab correcto y abre AWS Console desde ese laboratorio. Selecciona `us-east-1` (o la región acordada con el docente) y confirma que el laboratorio siga activo durante el trabajo.
2. Completa el inventario de las secciones 2 y 3. Identifica el stack que administra RDS y registra qué recursos SNS/SQS ya existen.
3. Si existe una topología parcial, recursos con los mismos nombres o no sabes quién administra un recurso, no crees ni actualices el stack. Pide al docente que defina cómo completar esa topología sin reemplazos.
4. Si se confirmó que no existe la mensajería y tienes autorización para crearla, usa la plantilla aislada descrita abajo. No uses `aws-skates-platform.yaml` para esta tarea.

### B. Crear la topología aislada con CloudFormation

1. En AWS Console abre **CloudFormation → Stacks → Create stack → With new resources (standard)**.
2. Sube `Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-messaging.yaml` desde la carpeta del proyecto. Si AWS ofrece validar/revisar la plantilla, hazlo antes de avanzar.
3. Asigna un nombre de stack acordado, por ejemplo `skates-messaging-dev`. Usa un `EnvironmentName` corto que no choque con nombres existentes.
4. Conserva inicialmente los valores documentados: `MaxReceiveCount=5`, `QueueRetentionSeconds=345600`, `DeadLetterRetentionSeconds=1209600`, `QueueVisibilityTimeoutSeconds=60` y `LongPollingSeconds=20`, salvo que la pauta o el docente requieran otros. El consumidor backend y `MaxReceiveCount` deben quedar alineados.
5. En la página de revisión, comprueba que los recursos sean solo el topic SNS, seis colas SQS (tres principales y tres DLQ), políticas, suscripciones, tres alarmas y el dashboard. Esta plantilla no crea un rol IAM para el backend.
6. Envía el stack solo con autorización. Espera `CREATE_COMPLETE`. Si falla, revisa **Events** y comparte el error sanitizado con el docente; no borres el stack ni recursos a ciegas.
7. En **Outputs**, registra `SnsTopicArn`, las seis URLs de cola y `CloudWatchDashboardName`. No publiques capturas con identificadores que el curso considere privados.
8. En **Resources**, SNS y SQS, comprueba que las tres suscripciones estén confirmadas, que los filtros correspondan a `PAYMENT_COMPLETED`/`PAYMENT_REJECTED`, `INVENTORY_UPDATED` y todos los eventos para Notifications, y que `RawMessageDelivery` esté habilitado. Revisa también `RedrivePolicy`, `VisibilityTimeout`, retención, long polling y política de cada cola.

### C. Configurar las credenciales e iniciar el backend

El SDK de Java utiliza la cadena de credenciales estándar de AWS; no se guardan Access Key, Secret Key ni Session Token en el código. Para ejecutar localmente, inicia el Learner Lab, renueva las credenciales temporales según el procedimiento oficial de AWS Academy y usa el perfil autorizado ya configurado. No pegues credenciales en un `.env`, README, captura o chat. En un entorno alojado en AWS, prefiere un rol asociado al recurso de ejecución.

En PowerShell, desde `Skates-backend-tienda_Sanchez_Cerda`, establece las variables de la aplicación usando los Outputs reales (reemplaza los marcadores; no guardes estos comandos con valores sensibles):

```powershell
$env:AWS_PROFILE = "academy"
$env:AWS_REGION = "us-east-1"
$env:MESSAGING_ENABLED = "true"
$env:SNS_TOPIC_ARN = "<SnsTopicArn>"
$env:SQS_ORDERS_QUEUE_URL = "<OrdersQueueUrl>"
$env:SQS_ORDERS_DLQ_URL = "<OrdersDlqUrl>"
$env:SQS_INVENTORY_QUEUE_URL = "<InventoryQueueUrl>"
$env:SQS_INVENTORY_DLQ_URL = "<InventoryDlqUrl>"
$env:SQS_NOTIFICATIONS_QUEUE_URL = "<NotificationsQueueUrl>"
$env:SQS_NOTIFICATIONS_DLQ_URL = "<NotificationsDlqUrl>"
$env:SQS_MAX_RECEIVE_COUNT = "5"
```

El backend también necesita `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` para la base MySQL existente. No copies esos secretos a la guía ni a Git. En la misma terminal inicia el modo que corresponda:

```powershell
# Publicador/API de tienda y pagos:
$env:SPRING_PROFILES_ACTIVE = "all"
.\mvnw.cmd spring-boot:run
```

Para consumir SQS, abre una segunda terminal, configura en ella las mismas variables AWS, SNS/SQS y de base de datos, y ejecuta:

```powershell
$env:SPRING_PROFILES_ACTIVE = "worker"
$env:SERVER_PORT = "0"
.\mvnw.cmd spring-boot:run
```

Para demostrar dos workers, abre una tercera terminal con la misma configuración `worker` y `SERVER_PORT=0`. Los dos deben apuntar a las mismas colas y base de datos. La pausa de consumo se comparte mediante esa base. `MESSAGING_ENABLED` permanece `false` por defecto; si falta una URL/ARN o falla el acceso AWS, corrige esa configuración antes de continuar. Al cerrar la sesión del Learner Lab, las credenciales temporales pueden expirar.

La API administrativa aparece con el perfil `admin` o `all` y mensajería habilitada, bajo `/api/admin/messaging`; exige un usuario Cognito con rol `ADMIN` y además permisos IAM para las operaciones AWS. El perfil `admin` incluye también el CRUD de productos para que el panel actual siga funcionando y sus cambios puedan publicar `INVENTORY_UPDATED`. Algunas rutas eliminan recursos o purgan mensajes. No las uses para aprovisionar la topología de la plantilla ni para pruebas destructivas; utiliza solo recursos de prueba y autorización expresa. No concedas `*` ni permisos administrativos generales al backend.

Para el despliegue con procesos separados, consulta en el [README del backend](../Skates-backend-tienda_Sanchez_Cerda/README.md) la tabla de perfiles/puertos: `catalog` 8081, `commerce` 8082, `admin` 8083 y dos instancias `worker` en 8084/8085. El proceso frontend debe enrutar las llamadas API al proceso `admin`; los workers comparten URLs de cola y base MySQL. En EC2, asigna un instance profile IAM a cada proceso que necesite AWS, limita sus acciones al topic/colas de la aplicación y define variables de entorno fuera del repositorio. No guardes credenciales temporales de Academy en una unidad systemd.

### D. Probar los flujos y guardar evidencia

1. Usa solo topic/colas de evaluación o de prueba autorizados. Publica un evento de pago de prueba `PAYMENT_COMPLETED`: debe llegar a Orders y Notifications, no a Inventory. Publica `INVENTORY_UPDATED`: debe llegar a Inventory y Notifications, no a Orders. No uses pagos ni datos personales reales.
2. Comprueba en logs que cada consumidor procese el evento y confirme el mensaje. Verifica que un evento duplicado no duplique el efecto de negocio.
3. Para probar DLQ, acuerda previamente un evento de prueba y un fallo controlado con el docente. Comprueba los intentos y la llegada a la DLQ; conserva logs y no purgues la cola para limpiar la evidencia.
4. Con dos procesos `worker` ejecutándose, publica varios eventos de prueba, detén uno de los procesos y muestra que el otro continúa. Verifica en logs que no haya pérdida y que los duplicados sean idempotentes.
5. Abre el dashboard cuyo nombre aparece en Outputs y las alarmas DLQ. Registra métricas y estado. La plantilla crea alarmas de CloudWatch, pero no configura un destino de notificación (por ejemplo, SNS/email); si la evaluación exige notificación activa, eso debe acordarse y añadirse como cambio separado.
6. Captura consola, Outputs, suscripciones/filtros, políticas/redrive, logs de ambos workers, DLQ y dashboard. Oculta credenciales, tokens y datos personales.
7. Revisa la rúbrica con la [auditoría](./messaging-audit.md) y marca cada criterio solo cuando tengas evidencia real. Los tests locales y el parseo de plantilla no sustituyen estas pruebas AWS.

## 8. Qué quedó hecho en el proyecto y dónde

- [aws-skates-messaging.yaml](../Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-messaging.yaml): nueva plantilla aislada SNS/SQS/CloudWatch, sin RDS. Solo se parseó y verificó estructuralmente en local; no está desplegada.
- [aws-skates-platform.yaml](../Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-platform.yaml): plantilla preexistente combinada; se alineó el parámetro `MaxReceiveCount` y se restringió el origen de la política de DLQ de entrega SNS de Orders. No la despliegues completa para esta tarea.
- `src/main/java/cl/duoc/backendskatesapp/messaging/QueueMessagePollingService.java` y `messaging/{orders,inventory,notifications}/`: se corrigió el orden de procesamiento/ACK y el envío a DLQ; el polling quedó separado por dominio con el SDK.
- `src/main/java/cl/duoc/backendskatesapp/service/AwsMessagingAdminService.java`: se alineó el `maxReceiveCount` y la adición de permiso SNS conserva la política SQS preexistente.
- `src/main/java/cl/duoc/backendskatesapp/config/MessagingProperties.java`: configuración tipada del topic, URLs y parámetros de mensajería.
- `src/main/java/cl/duoc/backendskatesapp/service/MessagingOverviewService.java` y `controller/MessagingAdminController.java`: endpoint `GET /api/admin/messaging/overview`, protegido como el resto del administrador y de solo lectura sobre atributos SQS e historial de `processed_messages`.
- `Skates-tienda-admin_Sanchez_Cerda/src/MessagingPanel.tsx` y `src/App.tsx`: pestaña SNS/SQS con conteos de colas/DLQ, últimos 50 eventos procesados sin payload y acciones para pausar/reanudar; no lee ni retira cuerpos SQS.
- `src/test/java/cl/duoc/backend_skates_app/`: pruebas de polling, overview, publicador SNS, administrador y control de pausa.
- [README.md](../Skates-backend-tienda_Sanchez_Cerda/README.md): documenta configuración, perfiles y arranque de workers.
- [messaging-audit.md](./messaging-audit.md), [messaging-validation-report.md](./messaging-validation-report.md) y los archivos de [evidencias](./evidencias): contienen la matriz de requisitos, resultados locales y lo que continúa bloqueado.

Las pruebas locales ejecutadas fueron: backend `.\mvnw.cmd clean test` (26 pruebas, 0 fallos, 0 errores; 2 omitidas por falta de Docker/Testcontainers), build y lint del panel admin tras este cambio, build/lint previos de ambos frontends y parseo/comprobaciones estructurales de las plantillas. No se crearon, modificaron ni probaron recursos AWS reales. También falta auditar Git: esta copia del proyecto no contiene `.git` y Git no estaba disponible en ese entorno.
