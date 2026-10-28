# 24 — Alta de clientes y obras con revisión

[Índice](README.md) · [ADR 0016](adr/0016-alta-de-clientes-y-obras-con-revision.md)

Implementación autorizada el 26 de octubre de 2026: autorregistro con revisión, conversión de contacto en cliente/obra, alta asistida y acceso directo a la obra tras activar la cuenta. La evidencia ejecutada y los límites se completan al terminar la implementación; este documento no acredita pruebas todavía.

## Recorrido acordado

1. Una persona solicita una reunión desde Contacto sin crear una cuenta, o se registra y solicita acceso desde su espacio privado.
2. Gerencia revisa Solicitudes y Contactos en Usuarios. Puede aprobar para una obra existente, rechazar, o crear cliente y obra con los datos ya completados.
3. Crear cliente y obra permite reutilizar una ficha existente y elegir «Habilitar acceso al cliente». La representación contractual requiere selección explícita.
4. Se conserva la obra en borrador y se prepara la activación en una cola durable. La persona verifica su correo y establece su contraseña en Keycloak; una identidad existente conserva sus credenciales.
5. El acceso aprobado completa los vínculos con el cliente y esa obra. El cliente entra directamente a su obra. Usuarios permite consultar estado y retirar el acceso sin eliminar la historia.

La integración de correo e IdP debe estar configurada para enviar activaciones; la interfaz distingue pendiente, envío y activación. Registrar una cuenta no otorga permisos sobre obras.
