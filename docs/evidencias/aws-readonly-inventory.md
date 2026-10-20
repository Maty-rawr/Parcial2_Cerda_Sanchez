# Evidencia de consulta de AWS (solo lectura)

**Fecha:** 2026-10-20  
**Perfil probado:** `academy`  
**Región configurada:** `us-east-1`  
**Escrituras en AWS:** ninguna.

## Validación de acceso

- `aws --version`: **APROBADO** — AWS CLI 2.38.1 disponible.
- `aws sts get-caller-identity --profile academy`: **APROBADO** — autenticación STS correcta. El identificador de cuenta y ARN se omiten deliberadamente de esta evidencia.
- `aws configure get region --profile academy`: **APROBADO** — `us-east-1`.

## Inventario solicitado

Todos los comandos se ejecutaron con `--profile academy` y `--region us-east-1`. Cada llamada de inventario terminó en denegación explícita de política de identidad. No se intentaron otros métodos ni operaciones para eludir las restricciones.

| Consulta | Comando | Estado | Resultado |
|---|---|---|---|
| Stacks y recursos CloudFormation | `aws cloudformation list-stacks ...` | **BLOQUEADO** | `AccessDenied`, explícito para `cloudformation:ListStacks`. |
| Topics SNS y suscripciones | `aws sns list-topics ...` | **BLOQUEADO** | `AuthorizationError`, explícito para `SNS:ListTopics`. No fue posible enumerar suscripciones ni atributos de filtros. |
| Colas SQS, atributos, redrive y políticas | `aws sqs list-queues ...` | **BLOQUEADO** | `AccessDenied`, explícito para `sqs:ListQueues`. No fue posible leer colas ni atributos. |
| Alarmas de CloudWatch | `aws cloudwatch describe-alarms ...` | **BLOQUEADO** | `AccessDenied`, explícito para `cloudwatch:DescribeAlarms`. |
| Dashboards/métricas | No se consultaron después de la denegación de lectura de CloudWatch. | **NO EJECUTADO** | Evitar consultas adicionales fuera de los permisos disponibles. |

AWS reportó en las denegaciones una política de identidad explícita denominada `voc-cancel-cred`; no se incluye el ARN completo del principal ni otros identificadores del estudiante. Una denegación explícita no se resuelve cambiando el comando, la región o el perfil. Se debe solicitar al administrador/docente de AWS Academy que revise el estado del Learner Lab y su política/permisos, o aportar capturas sanitizadas de la consola con los recursos.

## Conclusión

La identidad puede autenticarse, pero no tiene permiso para consultar el inventario necesario. Por ello no se puede afirmar que ningún stack, tópico, cola, suscripción, DLQ, alarma o dashboard exista, ni preparar un plan de cambios AWS seguro. No se desplegó, creó, modificó, eliminó ni purgó ningún recurso.
