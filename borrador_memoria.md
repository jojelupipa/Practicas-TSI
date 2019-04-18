# Portada

<nombres y grupo>

# Descripción general de la solución

Hemos propuesto como solución la creación de un agente principalmente
deliberativo que es capaz de resolver los niveles del juego *Boulder
Dash* elaborando rutas que oscilan entre el camino óptimo y el más
seguro posible.

Además, sobre las clases proporcionadas para la búsqueda se han
realizado modificaciones a fin de optimizar los tiempos de
búsqueda. Por ejemplo dada la naturaleza dinámica del mapa se ha
sustituido la búsqueda inicial de todos los caminos entre cualesquiera
dos casillas por una búsqueda exclusiva de la posición actual del
jugador a la posición al destino deseado, que se puede ejecutar en
cualquier momento necesario sin consumir el tiempo disponible.

Para definir el comportamiento del agente hemos creado un conjunto de
heurísticas, tanto para elegir qué gema coger como para decidir qué
camino tomar. Cada una con sus respectivos parámetros que se
explicarán más adelante.

La estrategia de búsqueda se podría agrupar en las siguientes
secciones:

## Nuestro turno

Cuando nos toca elegir una acción recibimos el estado del mapa y un
cronómetro para poder tener en cuenta el tiempo límite en caso de
necesidad. Como el mapa puede haber cambiado tenemos que actualizarlo,
comprobar el estado y decidir qué tipo de razonamiento hay que hacer,
si hay que coger gemas, ejecutar un plan, buscar la salida o huir.

```
grid = actualizaGrid(); // Actualizamos el mapa

if (hayPiedra()) {
	// Nos ponemos a salvo
}

if (noHayPlan()) {
	// Pensamos un plan para buscar gemas o la salida
}

if (hayPlan()) {
	// Ejecutamos el plan
}	
```

# Comportamiento Reactivo

Nuestro agente incorpora un conjunto de elementos que definen su
comportamiento reactivo. Entre estos comportamientos se encuentran el
ponerse a salvo cuando se encuentra algún peligro o cuando se quede
bloqueado o sin camino disponible.

## Piedra encima

Si tenemos una piedra sobre el personaje es necesario ponerse a salvo
para evitar perder. Nuestro agente en este caso realiza un estudio
rápido de las casillas adyacentes a las que podamos
desplazarnos, dándole prioridad a la última posición en la que
estábamos a salvo y quitándosela a las casillas que tengan rocas o
enemigos cerca (puede considerarse que este comportamiento tiene una
carga deliberativa al evaluar cuál la mejor casilla a la que
desplazarse). 

## Bloqueo

Para reducir el tiempo de cómputo no calculamos el camino a la gema
más acertada en cada iteración, sino que cuando ocurran determinados
eventos se vuelve a lanzar la búsqueda del camino. Estos eventos son
tanto la consecución de alguna gema o la ausencia de plan tras una
huída, pero también puede ser que se haya interpuesto una roca en el
camino de manera inesperada. Para prevenir un bloqueo permanente se
utiliza un contador que se incrementa en cada turno que no cambiamos
de casilla y se reinicia cada vez que nos movemos. Cuando este
contador llega a un valor determinado (5 ticks) se vuelve a lanzar el
algoritmo de búsqueda para salir del bloqueo.



# Comportamiento Deliberativo

El agente desarrollado utiliza varios métodos para razonar en tanto a
su situación en cada momento del juego. Todos estos definen el
comportamiento deliberativo del mismo. Elegir qué gema hay que coger,
elegir qué camino tomar, analizar las zonas más peligrosas son
algunos de estos procedimientos.

## Selección de gema

A la hora de elegir una gema estas se ordenan asignándoles una
prioridad, y esta prioridad viene dada por la distancia al personaje,
la presencia de rocas sobre esta gema, la cercanía a otras gemas o la
presencia de enemigos en el área, así como también se tiene en cuenta
si la gema es accesible o no (si existe un camino despejado a la
gema). Todos estos factores están ponderados por unas constantes que
le asignan un grado de importancia.

Respecto a la presencia de enemigos cercanos es destacable cómo se ha
realizado esta búsqueda, pues es una de las más costosas en términos
de ejecución y se reutiliza en procesos posteriores: 

Esta búsqueda consiste en la realización de una búsqueda recursiva con
un factor de ramificación para limitar la profundidad de la
búsqueda. Partimos de la casilla a analizar. Comprobamos si sus
vecinos son casillas excavadas, en caso afirmativo se exploran sus
vecinos (que no hayan sido ya explorados) para comprobar la presencia
de enemigos. Si se explora todo el área “libre” o se llega a la
profundidad límite se devuelve el número de enemigos encontrados. 

## Búsqueda de caminos

Para realizar la búsqueda de caminos se ha partido del algoritmo A*
que se proporcionaba para la práctica pero hemos realizado unos
cuantos cambios en la heurística para mejorar la selección del
camino. Para esto modificamos directamente las clases AStar y
Pathfinder, añadiendo a la primera clase, donde se calcula la
heurística, unas condiciones que aumentan el coste de un nodo cuando
este tiene enemigos cerca (con el mismo método que se usaba en la
selección de la gema) o cuando tiene alguna roca encima. Esto hace que
se eviten caminos que pasan por debajo de rocas o que puedan liberar a
algún enemigo, pero sin llegar a descartarlos para cuando no haya más alternativas.
