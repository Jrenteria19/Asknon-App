# Asknon-App

App para resolver preguntas anónimas de alumnos.

## Descripción del Proyecto

Asknon-App es una aplicación integral diseñada para crear un canal de comunicación seguro y anónimo entre alumnos y educadores. Permite a los estudiantes formular preguntas sin revelar su identidad, fomentando un ambiente donde puedan abordar dudas o inquietudes que de otra manera no expresarían. La aplicación está planeada para tener presencia en diferentes plataformas (Móvil, Android TV y Wear OS) para maximizar su accesibilidad.

## Tecnologías Utilizadas

Este proyecto se desarrolla utilizando un stack tecnológico moderno y robusto para garantizar rendimiento y escalabilidad:

*   **Kotlin:** El lenguaje principal de desarrollo, conocido por su concisión y seguridad.
*   **Jetpack Compose:** El enfoque declarativo de Google para construir interfaces de usuario nativas en Android, utilizado para desarrollar las interfaces de usuario en las diferentes plataformas (Móvil, TV y Wear OS).
*   **Gradle:** La herramienta de automatización de construcción estándar en Android, utilizada para la gestión de dependencias, compilación y empaquetado del proyecto.
*   **Firebase:** (Considera añadir qué servicios de Firebase planeas usar, como Authentication, Firestore, Realtime Database, Functions, etc.) Firebase proporciona una suite de herramientas backend como servicio que pueden ser cruciales para manejar usuarios, datos y lógica de negocio en el backend.
*   **Retrofit/Ktor Client:** (Si planeas interactuar con APIs externas o un backend propio) Para realizar peticiones de red.

## Roles en el Proyecto

El equipo del proyecto Asknon-App está compuesto por los siguientes miembros y sus respectivas responsabilidades:

*   **Junior Renteria:**
    *   **Líder del Proyecto:** Coordina las actividades del equipo, toma decisiones clave y asegura el avance del proyecto.
    *   **Desarrollador Backend (Móvil):** Responsable de la lógica del lado del servidor y las APIs que dan soporte a la aplicación móvil.
*   **Francisco Carrillo:**
    *   **Desarrollador Backend (TV y Wear OS):** Encargado de la lógica del lado del servidor y las APIs que soportan las versiones de la aplicación para Android TV y Wear OS.
*   **Tristan Ruelas:**
    *   **Desarrollador Frontend (Móvil y TV):** Responsable de la implementación de la interfaz de usuario y la experiencia del usuario en las plataformas móvil y Android TV utilizando Jetpack Compose.
*   **Juan Montes:**
    *   **Desarrollador Frontend (Wear OS y TV):** Encargado de la implementación de la interfaz de usuario y la experiencia del usuario en las plataformas Wear OS y Android TV utilizando Jetpack Compose.
*   **Brandon Escobedo:**
    *   **Pruebas y Testeo:** Responsable de diseñar y ejecutar casos de prueba para asegurar la calidad, estabilidad y correcto funcionamiento de la aplicación en todas sus plataformas.

## Instalación y Configuración

1.  Clona el repositorio: `git clone [URL del repositorio]`
2.  Abre el proyecto en Android Studio.
3.  Asegúrate de tener el SDK de Android configurado para las versiones necesarias (Móvil, TV y Wear OS).
4.  Sincroniza el proyecto con los archivos Gradle.
5.  Configura tus credenciales de Firebase si es necesario.
6.  Ejecuta la aplicación en los emuladores o dispositivos físicos correspondientes (Móvil, Android TV, Wear OS).

## Uso
La aplicación Asknon-App ofrece diferentes experiencias para profesores y alumnos a través de sus plataformas:

*   **Flujo General:** Al iniciar la aplicación, el usuario elige si es **profesor** o **alumno**.

*   **Rol Profesor (Móvil):**
    *   Al seleccionar el rol de profesor, se generará un **código de clase** único.
    *   El profesor podrá ver las **preguntas pendientes** enviadas por los alumnos.
    *   Tiene la opción de **proyectar** preguntas (mostrarlas en Android TV), **eliminar la clase** y ver las **preguntas aprobadas**.

*   **Rol Alumno (Móvil):**
    *   El alumno ingresa el **código de clase** proporcionado por el profesor.
    *   Si el código coincide, se abrirá una nueva pantalla donde podrá **enviar preguntas** de forma anónima.
    *   Actualmente, solo se permite enviar una pregunta por respuesta del profesor (aunque el flujo de "respuesta del profesor" no está detallado, se mantiene la limitación).

*   **Proyección en Android TV (Rol Profesor):**
    *   El profesor puede **aprobar** una pregunta pendiente.
    *   Al seleccionar la opción **"proyectar"**, la pregunta aprobada se mostrará en la pantalla de Android TV asociada a la misma clase.

*   **Uso en Wear OS (Rol Profesor):**
    *   Cuando los alumnos envían preguntas y estas llegan al estado "pendiente", el profesor recibirá notificaciones en su reloj inteligente con el **número total de preguntas pendientes**.
    *   Desde el reloj inteligente, el profesor tiene varias opciones para aprobar todas las preguntas pendientes:
        *   **Opción 1:** Agitar su teléfono (conectado al reloj).
        *   **Opción 2:** Tocar un botón específico en la interfaz del reloj ("aprobar todas").
        *   **Opción 3:** Agitar su reloj inteligente.
