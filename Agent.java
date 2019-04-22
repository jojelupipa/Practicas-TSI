package practica_busqueda;

import java.util.ArrayList;

import com.sun.org.apache.regexp.internal.RE;
import core.game.StateObservation;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Pair;
import tools.Vector2d;
import java.util.Collections;

public class Agent extends BaseAgent {
	// Valor añadido a la heurística de gemas: si tiene una piedra encima
	//private final static int HAYPIEDRA = 0;
	// Valor añadido a la heurística de gemas: si hay mas gemas alrededor
	//private final static int HAYGEMAS = 0;
	// Valor añadido a la heurística de gemas: si hay enemigos en el área
	private final static int HAYENEMIGOS = 120;
	// Valor añadido a la heurística de gemas: si el camino es inaccesible
	private final static int GEMINACCESIBLE = 3000;
	// Valor añadido a la heurística de gemas: si el camino pasa por casillas con piedras encima
	private final static int CAMINOPIEDRAS = 100;
	// Nº de gemas necesarias para ganar
	private final static int REQUIREDGEMS = 9;
	// Nº de ticks antes de crear otro plan
	private final static int NTICKSESPERA = 7;
	// Nº de ticks antes de pasar a modo quitar rocas
	private final static int NTICKSROCAS = 20;
	// Nº de ticks max esperando por culpa de enemigos
	private final static int NTICKSENEMIGOS = 3;
	// Nº de ticks max esperando por culpa de enemigos (modo quitar rocas)
	private final static int NTICKENEMIGOSROCAS = 5;
	// Algoritmo A*
	private PathFinder pF;
	// El plan (camino) a seguir
	private ArrayList<Node> path = new ArrayList<>();
	// La última posición del jugador
	private PlayerObservation ultPos;
	// EL tablero de juego
	private ArrayList<Observation> grid[][];
	// El estado actual del juego
	private StateObservation state;
	// Nº de turnos quieto
	private int nQuieto;
	// Si no puede ir a ninguna gema y tiene que quitar rocas para abrir camino
	private boolean quitarRocas;
	// Si puede pensar o no un plan (si no es que ha reaccionado ante algo)
	private boolean piensa;
	// Objetivo actual a la que ir (puede ser null)
	private Observation objActual;
	// Tiempo (ticks)
	private int t;
	// Lista de piedras para saber si ha habido cambios
	private ArrayList<Observation> piedras = new ArrayList<>();
	// Lista de gemas para saber si ha habido cambios
	private ArrayList<Observation> gemasAct = new ArrayList<>();

	// --------------------------------------------------------------------
	// Constructor
	// --------------------------------------------------------------------
	public Agent(StateObservation sO, ElapsedCpuTimer elapsedTimer) {
		super(sO, elapsedTimer);
		state = sO;
		grid = getObservationGrid(state);
		ultPos = getPlayer(state);
		ArrayList<Integer> tipoObs = new ArrayList();
		// Obstaculos a evitar por el pathfinder
		tipoObs.add(0);
		tipoObs.add(7);
		tipoObs.add(11);
		tipoObs.add(10);
		pF = new PathFinder(tipoObs);
		pF.run(state);
		nQuieto = 0;
		piensa = true;
		objActual = null;
		quitarRocas = false;
		piedras = getBouldersList(state);
		gemasAct = getGemsList(state);
		t = 0;
		//tUltAct = 0;
		// Primera ejecucción, buscamos los caminos a la gema para ahorrar tiempo
		for (Observation gem : getGemsList(state))
			findPath(ultPos, gem);
	}
	// --------------------------------------------------------------------
	// Funcion act
	// --------------------------------------------------------------------
	@Override
	public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		// Actualizamos el estado del agente
		update(stateObs);
		// Aplica la rutina reactiva
		reactivo();
		// Aplica la rutina deliberativa
		deliberativo();
		// Evita situaciones de riesgo
		Node resRiesgo = evitarRiesgo();
		// Determina la siguiente acción del plan o del resultado de evaluarRiesgo
		Types.ACTIONS sigAccion = siguienteAccion(resRiesgo);

		// Actualizamos la última posición
		ultPos = getPlayer(state);
		// Puede volver a pensar para el siguiente turno
		piensa = true;
		// Aumento nº de ticks
		++t;
		if (t > 165) {
			try { Thread.sleep(0); } catch (InterruptedException e) { System.out.println(e); }
		}
		// System.out.println("T: " + elapsedTimer.elapsedMillis() + ", R: " + elapsedTimer.remainingTimeMillis());
		return sigAccion;
	}
	// --------------------------------------------------------------------
	// Funciones principales
	// --------------------------------------------------------------------
	// Actualiza el agente
	private void update(StateObservation stateObs) {
		// Actualizamos el estado, grid, pathfinder
		state = stateObs;
		grid = getObservationGrid(state);
		pF.run(state);
		PlayerObservation jugador = getPlayer(state);
		// Si el agente se mueve se elimina la acción del plan, si no es que está quieto un turno
		if ((jugador.getX() != ultPos.getX() || jugador.getY() != ultPos.getY()) && !path.isEmpty()) {
			//System.out.println("hola");
			path.remove(0);
			nQuieto = 0;
		} else
			++nQuieto;
		// Modo rocas si supero el tiempo
		if (nQuieto > NTICKSROCAS) {
			objActual = null;
			nQuieto = 0;
			quitarRocas = true;
		}
		// Si hay un cambio o paso de espera recalculo objetivo
		if (rocasActualizadas() || gemasActualizadas() || nQuieto > NTICKSESPERA) {
			objActual = null;
			quitarRocas = false;
		}
	}

	// --------------------------------------------------------------------
	// El agente reacciona si tiene un enemigo justo al lado o una roca encima
	private void reactivo() {
		PlayerObservation jugador = getPlayer(state);
		Orientation orJ = jugador.getOrientation();
		// Enemigo al lado
		boolean enemigoProximo = nEnemigoCerca(jugador) > 0;
		// Roca encima
		boolean rocaEncima = esPiedra(casillaDir(jugador, Orientation.N, 1));

		// Si alguna de las condiciones se cumple intenta escapar
		if (enemigoProximo || rocaEncima) {
			// Reactivo on
			piensa = false;
			// Limpio plan y me muevo hacia donde me diga el plan de escape
			path.clear();
			path.add(evaluarEscape());
		}
	}
	// --------------------------------------------------------------------
	// Plan deliberativo
	private void deliberativo() {
		// Si puedo pensar y no tengo objetivo
		if (piensa) {
			PlayerObservation jugador = getPlayer(state);
			if (objActual == null) {
				int nGems = getNumGems(state);
				// Hay que quitar rocas
				if (quitarRocas) {
					// Miro las casillas libres debajos de las rocas y cojo la mas cercana con camino accesible
					ArrayList<Node> casillasBuenas = new ArrayList<>();
					for (Observation piedra : piedras) {
						Node casillaBuena = casillaDir(piedra, Orientation.S, 1);
						if (!esObstaculo(casillaBuena))
							casillasBuenas.add(casillaBuena);
					}
					if (!casillasBuenas.isEmpty()) {
						ordenarCasillas(casillasBuenas);
						Node nJugador = new Node(new Vector2d(jugador.getX(), jugador.getY()));
						ArrayList<Node> pathAux = null;
						int i;
						for (i = 0; i < casillasBuenas.size() && pathAux == null; ++i)
							pathAux = findPath(nJugador, casillasBuenas.get(i));
						Node sol = casillasBuenas.get(i);
						objActual = grid[(int) sol.position.x][(int) sol.position.y].get(0);
					} else
						objActual = jugador;
					// Si hay que buscar gemas
				} else if (nGems < REQUIREDGEMS) {
					// Obtengo la lista de gemas y las ordenamos segun la heurística
					ArrayList<Pair<Observation, Integer>> gemList = new ArrayList<>();
					for (Observation gem : gemasAct) {
						findPath(jugador, gem);
						gemList.add(new Pair<>(gem, heuristicaGema(gem)));
					}
					ordenarGemas(gemList);
					objActual = gemList.get(0).first;
				} else
					objActual = getExit(state);
			}
			// Camino al objetivo
			path = findPath(jugador, objActual);
			// Si el camino es nulo no hace nada
			if (path == null)
				path = new ArrayList<>();
		}
	}
	// --------------------------------------------------------------------
	// Transforma el plan en una acción
	private Types.ACTIONS siguienteAccion(Node resRiesgo) {
		Types.ACTIONS accion = Types.ACTIONS.ACTION_NIL;
		PlayerObservation jugador = getPlayer(state);
		if (!path.isEmpty()) {
			// Si resRiesgo no es null toma esa acción, si no, ejecuta el plan
			Node siguientePos = resRiesgo == null ? path.get(0) : resRiesgo;
			if (siguientePos.position.x != jugador.getX()) {
				if (siguientePos.position.x > jugador.getX())
					accion = Types.ACTIONS.ACTION_RIGHT;
				else
					accion = Types.ACTIONS.ACTION_LEFT;
			} else if (siguientePos.position.y != jugador.getY()) {
				if (siguientePos.position.y > jugador.getY())
					accion = Types.ACTIONS.ACTION_DOWN;
				else
					accion = Types.ACTIONS.ACTION_UP;
			}
		}
		return accion;
	}
	// --------------------------------------------------------------------ç
	// Evita una posible situación de riesgo preparandose en posición de huida pero sin moverse de casilla
	private Node evitarRiesgo() {
		Node res = null;
		PlayerObservation jugador = getPlayer(state);
		Orientation orJ = jugador.getOrientation();
		Node nJugador = new Node(new Vector2d(jugador.getX(), jugador.getY()));
		Node casillaMirando = casillaDir(nJugador, orJ, 1);
		// Solo si hay plan y hemos pensado
		if (!path.isEmpty() && piensa) {
			Node siguientePos = path.get(0);

			// Si hay camino entre enemigo y agente a 2 casillas en la misma dirección y si en la siguiente posición tenemos
			// un obstaculo o encima de él (bloquearía el escape si viene el enemigo)
			boolean sinEscape = enemigoPotencial(nJugador) && (esObstaculo(siguientePos) || esObstaculo(casillaDir(siguientePos, Orientation.N, 1)));
			// Si me puedo chocar con el enemigo
			boolean choqueEnemigo = nEnemigoCerca(siguientePos) > 0;
			// Lo mismo pero para siguientePos, además tiene que estar ya orientado para moverse
			Node sigSigPos = casillaDir(siguientePos, orJ, 1);
			boolean sinEscapeAvanzando = enemigoPotencial(siguientePos) && (esObstaculo(sigSigPos) || esObstaculo(casillaDir(sigSigPos, Orientation.N, 1)))
						&& casillaMirando.equals(siguientePos);
			// Si el siguiente movimiento es una casilla con una roca cayendo
			boolean piedraCayendo = esPiedra(casillaDir(siguientePos, Orientation.N, 1)) && estaExcavado(siguientePos) && !esGema(siguientePos);

			// Si hay riesgo potencial o tengo un enemigo al lado de la posición a la que voy, me preparo para huir
			if (sinEscape || choqueEnemigo) {
				res = evaluarEscape();
				// Si ya estoy mirando hacia el escape me quedo quieto;
				if (casillaMirando.equals(res))
					res = nJugador;
			} else if (piedraCayendo || sinEscapeAvanzando)
				res = nJugador;

			// Si llevo esperando un poco enrocado
			if ((sinEscape || choqueEnemigo || sinEscapeAvanzando) && nQuieto > NTICKSENEMIGOS) {
				if (nQuieto > NTICKENEMIGOSROCAS) {
					nQuieto = 0;
					quitarRocas = !quitarRocas;
				}
				objActual = null;
			}
		// Si estoy quieto y me viene un enemigo
		} else if (piensa && enemigoPotencial(nJugador) && (esObstaculo(casillaMirando) || esObstaculo(casillaDir(casillaMirando, Orientation.N, 1)))) {
			res = evaluarEscape();
			if (casillaMirando.equals(res))
				res = nJugador;
		}

		return res;
	}
	// --------------------------------------------------------------------
	// Subfunciones
	// --------------------------------------------------------------------
	// Devuelve si las rocas se han movido
	private boolean rocasActualizadas() {
		boolean res;
		ArrayList<Observation> nuevasPiedras = getBouldersList(state);
		res = nuevasPiedras.size() != piedras.size();
		if (!res)
			for (int i = 0; i < piedras.size() && !res; ++i)
				if (piedras.get(i).getX() != nuevasPiedras.get(i).getX() || piedras.get(i).getY() != nuevasPiedras.get(i).getY())
					res = true;
		piedras = nuevasPiedras;
		return res;
	}
	// --------------------------------------------------------------------
	// Devuelve si se ha cogido una gema
	private boolean gemasActualizadas() {
		ArrayList<Observation> nuevasGem = getGemsList(state);
		boolean res = nuevasGem.size() != gemasAct.size();
		gemasAct = nuevasGem;
		return res;
	}
	// --------------------------------------------------------------------
	// Devuelvo un nodo al que escapar
	private Node evaluarEscape() {
		Node res;
		PlayerObservation jugador = getPlayer(state);
		ArrayList<Node> vecinos = obtenerVecinos(jugador);
		if (!vecinos.isEmpty()) {
			// Asigno prioridades, ordeno y obtengo la más promotedora
			ArrayList<Pair<Node, Integer> > posibilidades = new ArrayList<>();
			for (Node vecino : vecinos) {
				posibilidades.add(new Pair<>(vecino, darPrioridad(vecino)));
			}
			ordenarPrioridad(posibilidades);
			res = posibilidades.get(0).first;
		} else
			// Si no hay vecinos posibles se queda quieto
			res = new Node(new Vector2d(jugador.getX(), jugador.getY()));
		return res;
	}
	// --------------------------------------------------------------------
	// Da una estimación (prioridad) de como de segura es la casilla vecino
	private int darPrioridad(Node vecino) {
		// Todas empiezan en 1
		int prioridad = 1;
		final int PIEDRAENCIMA = 300;
		final int ENEMIGOCERCA = 200;
		final int MISMADIR = -1;
		final int MISMADIRENEMIGO = -100;
		PlayerObservation jugador = getPlayer(state);
		Orientation orJ = jugador.getOrientation();

		// Ante empate se prioriza la casilla en la que se mira menos si hay un enemigo justo al lado del jugador
		if (casillaDir(jugador, orJ, 1).equals(vecino)) {
			if (nEnemigoCerca(jugador) > 0)
				prioridad += MISMADIR;
			else
				prioridad += MISMADIRENEMIGO;
		}
		// Si hay enemigos cerca del vecino
		if (nEnemigoCerca(vecino) > 0)
			prioridad += ENEMIGOCERCA;
		// Si tiene una roca encima
		if (esPiedra(casillaDir(vecino, Orientation.N, 1)))
			prioridad += PIEDRAENCIMA;

		return prioridad;
	}
	// --------------------------------------------------------------------
	// Funciones auxiliares
	// --------------------------------------------------------------------
	// Devuelve el camino entre origen y destino del pathFinder
	private ArrayList<Node> findPath(Vector2d origen, Vector2d destino) {
		return pF.findPath(origen, destino);
	}

	private ArrayList<Node> findPath(Node origen, Node destino) {
		return findPath(origen.position, destino.position);
	}

	private ArrayList<Node> findPath(Observation origen, Observation destino) {
		return findPath(new Vector2d(origen.getX(), origen.getY()), new Vector2d(destino.getX(), destino.getY()));
	}
	// --------------------------------------------------------------------
	// Devuelve los vecinos de un nodo (casillas posibles para mover)
	private ArrayList<Node> obtenerVecinos(Node n) {
		return pF.getNeighbours(n);
	}

	private ArrayList<Node> obtenerVecinos(Vector2d v) {
		return obtenerVecinos(new Node(v));
	}

	private ArrayList<Node> obtenerVecinos(Observation o) {
		return obtenerVecinos(new Vector2d(o.getX(), o.getY()));
	}
	// --------------------------------------------------------------------
	// Devuelve la casilla n veces en la dirección dada según la orientación
	// Nota: puede devolver una casilla fuera de rango
	private Node casillaDir(int x, int y, Orientation o, int n) {
		Node res = null;
		switch (o) {
			case N:
				res = new Node(new Vector2d(x, y - n));
				break;
			case E:
				res = new Node(new Vector2d(x + n, y));
				break;
			case W:
				 res = new Node(new Vector2d(x - n, y));
				 break;
			case S:
				res = new Node(new Vector2d(x, y + n));
				break;
		}
		return res;
	}

	private Node casillaDir(Vector2d v, Orientation o, int n) {
		return casillaDir((int) v.x, (int) v.y, o, n);
	}

	private Node casillaDir(Node nod, Orientation o, int n) {
		return casillaDir(nod.position, o, n);
	}

	private Node casillaDir(Observation obs, Orientation o, int n) {
		return casillaDir(obs.getX(), obs.getY(), o, n);
	}
	// --------------------------------------------------------------------
	// Devuelve si una casilla es de un tipo
	// Nota: si se sale fuera del tablero devuelve false
	private boolean esAlgo(int x, int y, ObservationType tipo) {
		if (x < 0 || x >= grid.length) return false;
		if (y < 0 || y >= grid[x].length) return false;

		for (Observation obs : grid[x][y])
			if (obs.getType() == tipo)
				return true;
		return false;
	}
	//--------------------------------------------------------------------
	// Si la casilla es obstáculo (pared, roca, murciélago o escorpión)
	private boolean esObstaculo(int x, int y) {
		return esAlgo(x, y, ObservationType.BOULDER) || esAlgo(x, y, ObservationType.WALL)
				|| esAlgo(x, y, ObservationType.BAT) || esAlgo(x, y, ObservationType.SCORPION);
	}

	private boolean esObstaculo(Vector2d v) {
		return esObstaculo((int) v.x, (int) v.y);
	}

	private boolean esObstaculo(Node n) {
		return esObstaculo(n.position);
	}

	private boolean esObstaculo(Observation obs) {
		return esObstaculo(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Nº de enemigos adyacentes
	private int nEnemigoCerca(int x, int y) {
		int nEnemigos = 0;

		Orientation orientaciones[] = new Orientation[]{Orientation.N, Orientation.S, Orientation.E, Orientation.W};

		for (Orientation orient : orientaciones)
			if (esEnemigo(casillaDir(x, y, orient, 1)))
				++nEnemigos;

		return nEnemigos;
	}

	private int nEnemigoCerca(Vector2d v) {
		return nEnemigoCerca((int) v.x, (int) v.y);
	}

	private int nEnemigoCerca(Node casilla) {
		return nEnemigoCerca(casilla.position);
	}

	private int nEnemigoCerca(Observation obs) {
		return nEnemigoCerca(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Si casilla es piedra
	private boolean esPiedra(int x, int y) {
		return esAlgo(x, y, ObservationType.BOULDER);
	}

	private boolean esPiedra(Vector2d v) {
		return esPiedra((int) v.x, (int) v.y);
	}

	private boolean esPiedra(Node nodo) {
		return esPiedra(nodo.position);
	}

	private boolean esPiedra(Observation obs) {
		return esPiedra(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Si es enemigo (murciélago o escorpión)
	private boolean esEnemigo(int x, int y) {
		return esAlgo(x, y, ObservationType.BAT) || esAlgo(x, y, ObservationType.SCORPION);
	}

	private boolean esEnemigo(Vector2d v) {
		return esEnemigo((int) v.x, (int) v.y);
	}

	private boolean esEnemigo(Node n) {
		return esEnemigo(n.position);
	}

	private boolean esEnemigo(Observation obs) {
		return esEnemigo(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Si está excavado es que no tiene tierra (ground) ni es una pared (wall) ni tampoco contamos las rocas (boulder)
	private boolean estaExcavado(int x, int y) {
		return !esAlgo(x, y, ObservationType.BOULDER) && !esAlgo(x, y, ObservationType.GROUND) && !esAlgo(x, y, ObservationType.WALL);
	}

	private boolean estaExcavado(Vector2d v) {
		return estaExcavado((int) v.x, (int) v.y);
	}

	private boolean estaExcavado(Node n) {
		return estaExcavado(n.position);
	}

	private boolean estaExcavado(Observation obs) {
		return estaExcavado(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Si es una gema
	private boolean esGema(int x, int y) {
		return esAlgo(x, y, ObservationType.GEM);
	}

	private boolean esGema(Vector2d v) {
		return esGema((int) v.x, (int) v.y);
	}

	private boolean esGema(Node n) {
		return esGema(n.position);
	}

	private boolean esGema(Observation obs) {
		return esGema(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Si es un jugador
	private boolean esJugador(int x, int y) {
		return esAlgo(x, y, ObservationType.PLAYER);
	}

	private boolean esJugador(Vector2d v) {
		return esJugador((int) v.x, (int) v.y);
	}

	private boolean esJugador(Node n) {
		return esJugador(n.position);
	}

	private boolean esJugador(Observation obs) {
		return esJugador(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Busca el tipo de cosas que se indica con n (0 gemas, 1 casillas sin excavar)
	private int buscarEnArea(int x, int y, int n) {
		int contador = 0;
		Orientation orientaciones[] = new Orientation[]{Orientation.N, Orientation.S, Orientation.E, Orientation.W};

		ArrayList<Vector2d> abiertos = new ArrayList<>();
		ArrayList<Vector2d> cerrados = new ArrayList<>();
		abiertos.add(new Vector2d(x, y));
		while (!abiertos.isEmpty()) {
			Vector2d actual = abiertos.get(0);
			abiertos.remove(0);
			cerrados.add(actual);

			for (Orientation orient : orientaciones) {
				Vector2d nuevo = casillaDir(new Node(actual), orient, 1).position;
				boolean condicion = false;
				if (n == 0)
					condicion = esGema(nuevo);
				else if (n == 1)
					condicion = estaExcavado(nuevo);
				if (condicion && !abiertos.contains(nuevo) && !cerrados.contains(nuevo)) {
					abiertos.add(nuevo);
					if (n == 0)
						++contador;
					else if (n == 1) {
						if (esEnemigo(nuevo))
							++contador;
						else if (esJugador(nuevo))
							return 0;
					}
				}
			}
		}
		return contador;
	}
	// --------------------------------------------------------------------
	// Busca gemas cerca en área
	private int nGemasCerca(int x, int y) {
		return buscarEnArea(x, y, 0);
	}

	private int nGemasCerca(Vector2d v) {
		return nGemasCerca((int) v.x, (int) v.y);
	}

	private int nGemasCerca(Node n) {
		return nGemasCerca(n.position);
	}

	private int nGemasCerca(Observation o) {
		return nGemasCerca(o.getX(), o.getY());
	}
	// --------------------------------------------------------------------
	// Busca enemigos en area
	// Nota: si el jugador está en al area cuenta como 0
	private int enemigosArea(int x, int y) {
		return buscarEnArea(x, y, 1);
	}

	private int enemigosArea(Vector2d v) {
		return enemigosArea((int) v.x, (int) v.y);
	}

	private int enemigosArea(Node n) {
		return enemigosArea(n.position);
	}

	private int enemigosArea(Observation obs) {
		return enemigosArea(obs.getX(), obs.getY());
	}
	// --------------------------------------------------------------------
	// Método para dar un valor heurístico a las gemas
	private int heuristicaGema(Observation gema) {
		int res = 0;
		PlayerObservation jugador = getPlayer(state);
		ArrayList<Node> camino = findPath(jugador, gema);

		//res += esPiedra(casillaDir(gema, Orientation.N, 1)) ? HAYPIEDRA : 0;
		//res += nGemasCerca(gema) > 0 ? HAYGEMAS : 0;
		res += nEnemigoCerca(gema) > 0 ? HAYENEMIGOS : 0;
		res += enemigosArea(gema) > 0 ? HAYENEMIGOS : 0;
		if (camino != null && !camino.isEmpty()) {
			res += camino.size();
			res += caminoConPiedras(camino) ? CAMINOPIEDRAS : 0;
		} else {
			res += GEMINACCESIBLE;
			res += gema.getManhattanDistance(jugador);
		}
		return res;
	}
	// --------------------------------------------------------------------
	// Si el camino tiene piedras (excepto la gema)
	private boolean caminoConPiedras(ArrayList<Node> camino) {
		for (Node nodo : camino)
			if (esPiedra(nodo) && !esGema(nodo))
				return true;
		return false;
	}
	// --------------------------------------------------------------------
	// Nos dice si hay un camino entre un enemigo al lado de alguno de los vecinos de casilla
	private boolean enemigoPotencial(Node casilla) {
		boolean hayPeligro = false;
		// Miramos los vecinos para ver si está excavado (camino libre) y hay un enemigo adyacente
		for (Node vecino : obtenerVecinos(casilla))
			if (estaExcavado(vecino) && nEnemigoCerca(vecino) > 0)
				hayPeligro = true;
		return hayPeligro;
	}
	// --------------------------------------------------------------------
	// Funciones para ordenar
	// --------------------------------------------------------------------
	// Ordena los vecinos según su prioridad
	private void ordenarPrioridad(ArrayList<Pair<Node, Integer> > listaPair) {
		Collections.sort(listaPair, (p1, p2) -> {
			int v1 = p1.second;
			int v2 = p2.second;
			if (v1 < v2)
				return -1;
			else if (v1 == v2)
				return 0;
			else
				return 1;
		});
	}
	// --------------------------------------------------------------------
	// Ordena la lista de gemas en función de la heuristica impuesta
	private void ordenarGemas(ArrayList<Pair<Observation, Integer>> gemList) {
		Collections.sort(gemList, (p1, p2) -> {
			int h1 = p1.second;
			int h2 = p2.second;
			if (h1 < h2)
				return -1;
			else if (h1 == h2)
				return 0;
			else
				return 1;
		});
	}
	// --------------------------------------------------------------------
	// Ordena la lista de las piedras según la distancia Manhattan hasta la piedra
	private void ordenarCasillas(ArrayList<Node> casillas) {
		Collections.sort(casillas, (c1, c2) -> {
			Observation o1 = grid[(int) c1.position.x][(int) c1.position.y].get(0);
			Observation o2 = grid[(int) c2.position.x][(int) c2.position.y].get(0);
			int d1 = o1.getManhattanDistance(gemasAct.get(0));
			int d2 = o2.getManhattanDistance(gemasAct.get(0));
			if (d1 < d2)
				return -1;
			else if (d1 == d2)
				return 0;
			else
				return 1;
		});
	}
}
