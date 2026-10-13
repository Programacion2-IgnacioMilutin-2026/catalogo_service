|**NOMBRE**|X01 -Autenticar Cuenta Tecnica y Obtener Configuracion|
| --- | --- |
|**Actor principal**|Tiempo/Arranque|
|**Actor secundario**|Servicio Catedra|
|**Descripcion**|Inicio de sesión de la cuenta técnica en el servidor de la cátedra para obtener el JWT y la integración que contiene las configuraciones. Lo realiza cada servicio por separado, al arrancar y cada vez que la cátedra rechaza el JWT (`401`) en otro caso de uso.|
|**Alcance**|Ejecutado por cada servicio por su cuenta|
|**Precondicion**|Debe existir la cuenta tecnica (se crea una sola vez, de forma manual) y su `login` y `password` deben estar en la configuración externa del servicio, no en el repositorio. |
|**Flujo**| **1-** El servicio lee de su configuración externa el `login` y el `password` de la cuenta técnica.<br>**2-** Hace el login técnico (`POST /api/authenticate`, con `rememberMe` en verdadero para obtener la vigencia larga).<br>**3-** La cátedra devuelve el JWT (`id_token`) y la integración, con `groupId`, Redis, Kafka, los topics y el estado, etc.<br>**4-** El servicio revisa que el estado sea `PROVISIONED`.<br>**5-** Se guardan la configuración y el JWT en memoria. |
| **Flujo alternativo** | **2.1, Credenciales incorrectas o integración inexistente (`401` en el login, `403` o `404` `STUDENT_INTEGRATION_NOT_FOUND`):** error de configuración; no se reintenta y se informa al profesor.<br>**2.2, La cátedra no está disponible (timeout o `503`):** reintentos limitados con espera creciente; el servicio queda degradado y no realiza operaciones que dependan de la cátedra.<br>**4.1, El estado es distinto de `PROVISIONED`:** no se habilita la integración, se reintenta con límite y no se inician operaciones que dependan de la cátedra.  |
|**Postcondicion**|Cada servicio guarda en memoria el 'groupId', la configuracion de Redis y Kafka y el JWT vigente|
