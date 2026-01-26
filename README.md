
BITÁCORA RETO 2ªEVALUACION

13/01/2026

Hemos repartido las tareas que vamos a realizar en grupo.
Hemos decidido que la web será sobre reservas a cursos de formación que se impartirán en físico en nuestras oficinas. Simularemos nuestras oficinas en nazaret para hacer el video para inglés, esa es la idea ahora mismo.
Hemos decidido limitar los cursos de formación a 4 como número realista y viable. Tenemos que decidir sobre qué van a tratar y pensar en qué forma darle, con qué elementos categorías etc para poder crear la base de datos.
Creado el repositorio de github, nos falta el nombre de la empresa. Tenemos que generar ideas, logotipos…etc.
Base del index hecho
Página de cursos creada y hecho enlaces con el index.
Creado un css distinto para cada página.

14/01/2026

Elección de nombre “lortu” para la empresa.
Tenemos que elegir en que dos servicios vamos a alojar la web y la base de datos (mysql). Deben de ir en sitios independientes, diferentes el uno del otro, uno para mysql y otro para la web. Nos dan la opción de usar la VM de nazaret que funciona con ubuntu.
Vamos a alojar la web en cloudflare usando la VM de cloudflare para MYSQL.
Hoy vamos a elegir los 4 cursos de formación que vamos ofrecer.
Creación del repositorio en GITHUB 🙂
Comenzamos a darle la identidad a la empresa. Comenzamos con la creación de isologo.
Hemos elegido los 4 cursos que van a ser:
Ciberseguridad
Java 
Programación para dispositivos móviles android
Introducción al big data
Aitor nos ha facilitado la dirección de la máquina virtual de Nazaret en la cual crearemos el servidor mysql.
Sostenibilidad  hecha de puta madre.
Puesto los cursos en el index.
Formulario hecho pero falta pulir últimos detalles 
HAY QUE HACER ORGANIGRAMA
HACER PLANIFICACIÓN PREVENTIVA. MÍNIMO 3 TABLAS. ELEGIR 3 PUESTOS Y HACER DE ESOS 3 O AGRUPAR 3 PUESTOS POR RIESGOS SIMILARES (PROFESORES, ADMINISTRACIÓN,etc)
EVALUACIÓN DE RIESGO CON TABLA  TODO HECHO . 
Agregar sobre,  términos de uso etc.


15/01/2026

Hemos descargado Dbeaver para hacer el modelado de la base de datos y conexión ssh con la VM de nazaret para crear la base de datos mysql.Necesitamos hablar con aitor para que abran los puertos o habiliten la VM porque por ahora no podemos conectarnos. A la espera de hablar con edu para saber si tenemos que crear un modelado de la base de datos en DIA (diagrama). Puede que pasemos a desarrollar la conexión y la base de datos con mysql workbench.
Comenzamos a crear el modelado DIA de la base de datos (modelo entidad relación).
Presentación de la página con su índice, 
Creación del login y registro y más, arreglos en la página.


16/01/2026

Acceso a la máquina virtual de Nazaret mediante CMD y configuración de usuario. Update de repositorios e instalación de mysql.
Configurada la VM de ubuntu de Nazaret y acceso garantizado desde mysql workbench.
Investigar cómo hostear la web en cloudflare.


21/01/2026

Añadido todas las cosas de php del registro.

22/01/2026

Creadas las tablas y los campos de la base de datos.
Configuración Maven.
Añadido template de cursos con toda la info.

23/01/2026

Eliminados archivos php ya que son innecesarios con el sistema maven y java.
Modificación de páginas y elaboración de webs nuevas.
Subida nueva rama a github.
Decisión final de hostear la web en local y usar la VM de Nazaret para la base de datos.
Igualado estilo de todas las webs para que se vean igual. Modificados navs, footers, etc para que tengan coherencia.
Búsqueda de imágenes para cada curso.


24/01/2026
Finalizado todos los cursos con su respectivo form
Css arreglados
Falta hacer sobre.css
Falta arreglar css una cosa del cursoswp.css
Imagenes puestas con sus cursos
Todo subido a la branch cambios-jp


25/01/2026
Todo el contenido arreglado y ordenado en carpetas
Css mas completos
Htmls mejores
Hechas bien las conexiones.
Hemos gestionado la web para que este coherente y todavia nos faltan retocar varias cosas como fotografias, hacer el formulario de inscripción para los alumnos.
Debido a la imposibilidad de acceder desde casa a la VM de nazaret pasamos a configurar una maquina virtual para el mysql en oracle cloud.
Finalmente se decide instalar la base de datos MySQL en aws academy, en el lab. Configuramos el acceso con clave .pem para poder acceder.
Web gestionada en la ruta de maven. Maven configurado y archivo de java creado correlativo a la base de datos.
Web gestionada en la ruta de maven. Maven configurado y archivo de java creado correlativo a la base de datos.

26/01/2026

Asociada ip elastica a la instancia de aws para mantener la ip fija siempre, si no con carga reinicio la ip cambiaba y el proyecto había que modificarlo constantemente.
Añadidas consultas al archivo app.java.
Detectado problema de seguridad donde el usuario y pass de la base de datos aws se había subido github comprometiendo la seguridad del proyecto y haciéndolo accesible a posibles hackeos por parte de bots. Pasamos a cambiar usuario y pass de acceso y a añadir un archivo .gitignore al proyecto para que no se suban los datos de acceso.

¡IMPORTANTE!
Para ejecutar el proyecto, renombra el archivo "db.properties.example" a "db.properties" y pon la clave de la base de datos.


