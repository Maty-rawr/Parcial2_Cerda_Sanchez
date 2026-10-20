# Validación local de la plantilla de mensajería

**Plantillas:** `Skates-backend-tienda_Sanchez_Cerda/infra/aws-skates-messaging.yaml` y `aws-skates-platform.yaml`  
**Tipo de validación:** parseo YAML y comprobaciones estructurales locales.  
**AWS:** no se desplegó ni se llamó a CloudFormation; las consultas AWS siguen bloqueadas por la política de la cuenta.

## Resultados

- Parseo YAML de ambas plantillas con tags CloudFormation `!Ref`, `!Sub` y `!GetAtt`: **APROBADO**.
- Conteos esperados: 1 topic SNS, 6 colas SQS, 3 `QueuePolicy`, 3 suscripciones SNS, 3 alarmas CloudWatch y 1 dashboard: **APROBADO**.
- Las tres colas principales tienen `RedrivePolicy` hacia su DLQ y `maxReceiveCount` parametrizado; retención, visibilidad y long polling toman parámetros: **APROBADO**.
- Filtros exactos (`PAYMENT_COMPLETED`/`PAYMENT_REJECTED` para Orders, `INVENTORY_UPDATED` para Inventory y los tres tipos para Notifications), además de raw delivery: **APROBADO**.
- Las políticas SQS limitan `sqs:SendMessage` a SNS y al ARN del topic esperado: **APROBADO**.
- No incluye RDS, EC2, VPC, Cognito ni API Gateway: **APROBADO**.

Estas comprobaciones no sustituyen `aws cloudformation validate-template`, un change set ni una validación de despliegue. La validación remota sigue **BLOQUEADA** por el explicit deny de la cuenta. No se afirma que la plantilla haya sido aceptada o desplegada por AWS.

En la plantilla combinada se verificó que la política de la DLQ de entrega de Orders limita el envío SNS al ARN de `OrdersSubscription`, y que esta suscripción depende de la política de la cola principal, sin una dependencia circular con la política de la DLQ.
