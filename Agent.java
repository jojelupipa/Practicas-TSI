package practica_busqueda;

import java.util.ArrayList;

import com.sun.org.apache.xpath.internal.operations.Or;
import core.game.StateObservation;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Pair;
import tools.Vector2d;

import java.util.Collections;


public class Agent extends BaseAgent {
	private final static int REQUIREDGEMS = 9;
	private PathFinder pF;
	private ArrayList<Node> path = new ArrayList<>();
	private PlayerObservation ultPos;
	private ArrayList<Observation> grid[][];
	private final static int HAYPIEDRA = 0;
	private final static int HAYGEMAS = -1;
	private final static int HAYENEMIGOS = 120;
	private final static int GEMINACCESIBLE = 1000;
	private final static int CAMINOPIEDRAS = 100;
	private int nQuieto;
	private boolean quitarRocas;
	private boolean piensa;
	private Observation gemaActual;
	private int t;
	

	public Agent(StateObservation sO, ElapsedCpuTimer elapsedTimer) {
		super(sO, elapsedTimer);
		//printObservationGrid(getObservationGrid(sO));
		// Define obstacles
		// Initialize pathfinder
		// Run pathfinder
		ArrayList<Integer> tipoObs = new ArrayList();
		tipoObs.add(0);
		tipoObs.add(7);
		// Murciélagos y escorpiones para esquivar en conjunto con el agente reactivo
		// Así al recalcular el camino evitará al enemigo
		tipoObs.add(11);
		tipoObs.add(10);
		pF = new PathFinder(tipoObs);
		pF.run(sO);
		ultPos = getPlayer(sO);
		grid = getObservationGrid(sO);
		nQuieto = 0;
		piensa = true;
		gemaActual = null;
		t = 0;
		quitarRocas = false;
	}

	@Override
	public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		grid = getObservationGrid(stateObs);

		Types.ACTIONS nextAction = Types.ACTIONS.ACTION_NIL;
		//Calculate current number of gems
		int nGems = getNumGems(stateObs);
		// Obtengo la posición del avatar y lo paso a un Vector2D
        PlayerObservation avatar = getPlayer(stateObs);
		Vector2d aPos = new Vector2d(avatar.getX(), avatar.getY());


		//System.out.println(avatar.getX() + ", " + avatar.getY());

		// Si el agente se mueve se elimina la acción del plan, si no entonces está quieto un turno
		if ((avatar.getX() != ultPos.getX() || avatar.getY() != ultPos.getY()) && !path.isEmpty()){
			path.remove(0);
			nQuieto = 0;
		} else
			++nQuieto;

		if (!piensa)
			piensa = true;

		// Si hay una piedra encima o un enemigo
		if ((aPos.y - 1 >= 0 && hayPiedra((int) aPos.x, (int) aPos.y - 1)) || enemigoCerca(new Node(aPos)) > 0) {
			//System.out.println("Tengo una piedra encima");
			path.clear();
			pF.run(stateObs);
			ArrayList<Node> casillasValidas = pF.getNeighbours(new Node(aPos));
			piensa = false;
			if (!casillasValidas.isEmpty()) {
				ArrayList<Pair<Node, Integer> > posibilidades = new ArrayList<>();
				for (Node casilla : casillasValidas) {
					posibilidades.add(new Pair<>(casilla, darPrioridad(stateObs, casilla)));
					//System.out.println("Casilla: (" + casilla.position.x + ", " + casilla.position.y + "), prioridad: " + darPrioridad(stateObs, casilla));
				}
				ordenarPrioridad(posibilidades);
				path.add(posibilidades.get(0).first);
				//System.out.println("Voy a x: " + path.get(0).position.x + ", " + path.get(0).position.y);
			}
		}

		// Elemento deliberativo: si no ha reaccionado, piensa un plan
		if ((piensa && !quitarRocas) || (piensa && nQuieto > 8)) {
			path.clear();
			pF.run(stateObs);
			if (nGems < REQUIREDGEMS) {
				// Obtengo la lista de gemas ordenadas por la distancia hacia el jugador y las ordenamos con nuestra heurística
				ArrayList<Observation> gemList = getGemsList(stateObs);
				if (nQuieto > 8 || gemaActual == null || !esGema(gemaActual.getX(), gemaActual.getY())) {
					for (Observation gema : gemList) {
						Vector2d gPos = new Vector2d(gema.getX(), gema.getY());
						findPath(aPos, gPos);
					}

					//for (Observation gema : gemList)
					//	System.out.println(gema);
					ordenarGemas(avatar, gemList);
					//System.out.println(gemList);
					// Buscamos la primera gema más prometedora
					gemaActual = gemList.get(0);
				}
				//System.out.println(gema);
				Vector2d gPos = new Vector2d(gemaActual.getX(), gemaActual.getY());
				//int id = getID(aPos) * 10000 + getID(gPos);
				path = findPath(aPos, gPos);
				if (path == null) {
					if (nQuieto > 30)
						quitarRocas = true;
					else
						path = new ArrayList<>();
				} else
					quitarRocas = false;
				//pF.astar.printPath(id, path);
			} else {
				// Obtengo la posición del portal más cercano y obtengo la rutaS
				Observation portal = getExit(stateObs);
				Vector2d pPos = new Vector2d(portal.getX(), portal.getY());
				path = findPath(aPos, pPos);
				if (path == null) {
					if (nQuieto > 30)
						quitarRocas = true;
					else
						path = new ArrayList<>();
				} else
					quitarRocas = false;
			}
		}

		if (piensa && quitarRocas) {
			pF.run(stateObs);
			path = abreCamino(stateObs);
		}

		// Si hay plan, lo ejecuto
		if (!path.isEmpty()) {
			// Ejecuto el plan
			Node siguientePos = path.get(0);
			//System.out.println(siguientePos.position.x + ", " +
			if (piensa) {
				if (enemigoCerca(siguientePos) > 0) {
					Orientation ori = getPlayer(stateObs).getOrientation();
					int x = (int) siguientePos.position.x - (int) aPos.x;
					int y = (int) siguientePos.position.y - (int) aPos.y;
					siguientePos = null;
					if ((ori == Orientation.N && y == -1) || (ori == Orientation.S && y == 1) || (ori == Orientation.E && x == 1) || (ori == Orientation.W && x == -1)) {
						ArrayList<Node> vecinos = pF.getNeighbours(new Node(aPos));
						if (!vecinos.isEmpty()) {
							ArrayList<Pair<Node, Integer>> posibilidades = new ArrayList<>();
							for (Node casilla : vecinos)
								posibilidades.add(new Pair<>(casilla, darPrioridad(stateObs, casilla)));
							ordenarPrioridad(posibilidades);
							siguientePos = posibilidades.get(0).first;
						}
					}
				} else {
					Orientation ori = getPlayer(stateObs).getOrientation();
					int x = (int) siguientePos.position.x - (int) aPos.x;
					int y = (int) siguientePos.position.y - (int) aPos.y;
					Node escape = new Node(new Vector2d(siguientePos.position.x + x, siguientePos.position.y + y));
					ArrayList<Node> vecinosSig = pF.getNeighbours(siguientePos);
					int i;
					for (i = 0; i < vecinosSig.size() && !vecinosSig.get(i).position.equals(aPos); ++i);
					vecinosSig.remove(i);
					boolean enemigosCerca = false;
					for (Node nodo : vecinosSig)
						enemigosCerca = enemigosCerca || (estaExcavado((int) nodo.position.x, (int) nodo.position.y) && enemigoCerca(nodo) > 0);
					if (enemigosCerca && (hayPiedra((int) escape.position.x, (int) escape.position.y - 1) || enemigoCerca(escape) > 0)) {
						boolean orientado = (ori == Orientation.N && y == -1) || (ori == Orientation.S && y == 1) || (ori == Orientation.E && x == 1) || (ori == Orientation.W && x == -1);
						if (orientado) {
							ArrayList<Node> vecinos = pF.getNeighbours(new Node(aPos));
							if (!vecinos.isEmpty()) {
								ArrayList<Pair<Node, Integer>> posibilidades = new ArrayList<>();
								for (Node casilla : vecinos)
									posibilidades.add(new Pair<>(casilla, darPrioridad(stateObs, casilla)));
								ordenarPrioridad(posibilidades);
								for (Pair<Node, Integer> casilla : posibilidades)
									if (!casilla.first.equals(siguientePos))
										siguientePos = casilla.first;
							} else
								siguientePos = null;
						}
						gemaActual = null;
					}
				}
			}

			if (siguientePos != null) {
				if (siguientePos.position.x != aPos.x) {
					if (siguientePos.position.x > aPos.x)
						nextAction = Types.ACTIONS.ACTION_RIGHT;
					else
						nextAction = Types.ACTIONS.ACTION_LEFT;
				} else {
					if (siguientePos.position.y > aPos.y)
						nextAction = Types.ACTIONS.ACTION_DOWN;
					else
						nextAction = Types.ACTIONS.ACTION_UP;
				}
			}
		}


		ultPos = avatar;

		//System.out.println(nextAction);
		//System.out.println("Gema objetivo: "( + gemaActual.getX() + ", " + gemaActual.getY() + ")");
		++t;
		if (t > 300) {
			try { Thread.sleep(2000); } catch (InterruptedException e) { System.out.println(e); }
		}
		return nextAction;
	}

	ArrayList<Node> abreCamino(StateObservation stateObs) {

		ArrayList<Observation> piedras = getBouldersList(stateObs);
		ArrayList<Observation> piedrasBuenas = new ArrayList<>();
		for (Observation piedra : piedras)
			if (!pF.isObstacle(piedra.getX(), piedra.getY() + 1))
				piedrasBuenas.add(piedra);
		ordenarPiedras(getPlayer(stateObs), piedrasBuenas);
		ArrayList<Node> path = null;
		Vector2d aPos = new Vector2d(getPlayer(stateObs).getX(), getPlayer(stateObs).getY());
		for (int i = 0; i < piedrasBuenas.size() && path == null; ++i) {
			Vector2d dist = new Vector2d(piedrasBuenas.get(i).getX(), piedrasBuenas.get(i).getY() + 1);
			path = findPath(aPos, dist);
		}
		if (path == null)
			return new ArrayList<>();
		else
			return path;
	}

	private int darPrioridad(StateObservation stateObs, Node casilla) {
		int prioridad = 0;
		PlayerObservation jugador = getPlayer(stateObs);
		Node nodJugador = new Node(new Vector2d(jugador.getX(), jugador.getY()));
		int x = (int) casilla.position.x - jugador.getX();
		int y = (int) casilla.position.y - jugador.getY();
		Orientation orientacion = jugador.getOrientation();
		boolean orientado = (orientacion == Orientation.E && x == 1) || (orientacion == Orientation.W && x == -1)
								|| (orientacion == Orientation.S && y == 1) || (orientacion == Orientation.N && y == -1);
		if (!orientado)
			prioridad += 1;
		if (enemigoCerca(nodJugador) == 0 && casilla.position.y - 1 >= 0 && hayPiedra((int) casilla.position.x, (int) casilla.position.y - 1))
			prioridad += 2;
		//if (casilla.position.y - 2 >= 0 && hayPiedra((int) casilla.position.x, (int) casilla.position.y - 2))
		//	prioridad += 3;
		if (enemigoCerca(casilla) > 0)
			prioridad += 4;
		if (esEnemigo((int) casilla.position.x, (int) casilla.position.y))
			prioridad += 100;
		return prioridad;
	}

	private int enemigoCerca(Node casilla) {
		int nEnemigos = 0;
		int[] x_arrNeig = new int[]{0,    0,    -1,    1};
		int[] y_arrNeig = new int[]{-1,   1,     0,    0};

		int x = (int) (casilla.position.x);
		int y = (int) (casilla.position.y);

		for (int i = 0; i < x_arrNeig.length; ++i) {
			if (esEnemigo(x + x_arrNeig[i], y + y_arrNeig[i]))
				++nEnemigos;
		}
		return nEnemigos;
	}

	private void printObservationGrid(ArrayList<Observation>[][] observationGrid) {
		for(int i = 0; i < observationGrid.length; i++) {
			for(int j = 0; j < observationGrid[0].length; j++) {
				System.out.print(observationGrid[i][j] + " ");
			}
			System.out.println();
		}
	}

	private boolean hayPiedra(int x, int y) {
		if (x < 0 || x >= grid.length) return false;
		if (y < 0 || y >= grid[x].length) return false;

		for (Observation obs : grid[x][y]) {
			if (obs.getType() == ObservationType.BOULDER)
				return true;
		}
		return false;
	}

	private boolean esEnemigo(int x, int y) {
		if (x < 0 || x >= grid.length) return false;
		if (y < 0 || y >= grid[x].length) return false;

		for (Observation obs : grid[x][y]) {
			if (obs.getType() == ObservationType.BAT || obs.getType() == ObservationType.SCORPION)
				return true;
		}
		return false;
	}

	private boolean estaExcavado(int x, int y) {
		if (x < 0 || x >= grid.length) return false;
		if (y < 0 || y >= grid[x].length) return false;

		boolean excavado = false;

		for (Observation obs : grid[x][y]) {
			excavado = excavado || obs.getType() == ObservationType.GROUND || obs.getType() == ObservationType.WALL
						|| obs.getType() == ObservationType.BOULDER;
		}
		return !excavado;
	}

	private int getID(Vector2d punto) {
		return ((int) punto.x) * 100 + ((int) punto.y);
	}

	private ArrayList<Node> findPath(Vector2d origen, Vector2d destino) {
		return pF.astar.findPath(new Node(origen), new Node(destino));
	}


	private boolean caminoConPiedras(ArrayList<Node> camino) {
		for (Node nodo : camino) {
			if (!esGema((int) nodo.position.x, (int) nodo.position.y) && hayPiedra((int) nodo.position.x, (int) nodo.position.y - 1))
					return true;
		}
		return false;
	}

	private int enemigosArea(int x, int y) {
		int[] x_arrNeig = new int[]{0,    0,    -1,    1};
		int[] y_arrNeig = new int[]{-1,   1,     0,    0};

		ArrayList<Node> abiertos = new ArrayList<>();
		ArrayList<Node> cerrados = new ArrayList<>();
		Node nOrigen = new Node(new Vector2d(x, y));
		abiertos.add(nOrigen);
		while (!abiertos.isEmpty()) {
			Node actual = abiertos.get(0);
			abiertos.remove(0);
			cerrados.add(actual);
			int xa = (int) actual.position.x;
			int ya = (int) actual.position.y;
			for (int i = 0; i < x_arrNeig.length; ++i) {
				int xn = xa + x_arrNeig[i];
				int yn = ya + y_arrNeig[i];
				if (dentroLimites(xn, yn) && estaExcavado(xn, yn) && !abiertos.contains(new Node(new Vector2d(xn, yn))) && !cerrados.contains(new Node(new Vector2d(xn, yn)))) {
					abiertos.add(new Node(new Vector2d(xn, yn)));
					if (esEnemigo(xn, yn)) {
						//System.out.println("Enemigo en (" + xn + ", " + yn + ")");
						return 1;
					} else if (esJugador(xn, yn))
						return 0;
				}
			}
		}
		return 0;
	}

	private boolean esJugador(int x, int y) {
		for (Observation obs : grid[x][y]) {
			if (obs.getType() == ObservationType.PLAYER)
				return true;
		}
		return false;
	}

	private boolean esGema(int x, int y) {
		for (Observation obs : grid[x][y]) {
			if (obs.getType() == ObservationType.GEM)
				return true;
		}
		return false;
	}

	private boolean dentroLimites(int x, int y) {
		return x >= 0 && x < grid.length && y >= 0 && y < grid[x].length;
	}


	private int nGemasCerca(int x, int y) {
		int nGemas = 0;

		int[] x_arrNeig = new int[]{0,    0,    -1,    1};
		int[] y_arrNeig = new int[]{-1,   1,     0,    0};

		ArrayList<Node> abiertos = new ArrayList<>();
		ArrayList<Node> cerrados = new ArrayList<>();
		Node nOrigen = new Node(new Vector2d(x, y));
		abiertos.add(nOrigen);
		while (!abiertos.isEmpty()) {
			Node actual = abiertos.get(0);
			abiertos.remove(0);
			cerrados.add(actual);
			int xa = (int) actual.position.x;
			int ya = (int) actual.position.y;
			for (int i = 0; i < x_arrNeig.length; ++i) {
				int xn = xa + x_arrNeig[i];
				int yn = ya + y_arrNeig[i];
				if (dentroLimites(xn, yn) && esGema(xn, yn) && !abiertos.contains(new Node(new Vector2d(xn, yn))) && !cerrados.contains(new Node(new Vector2d(xn, yn)))) {
					abiertos.add(new Node(new Vector2d(xn, yn)));
					++nGemas;
				}
			}
		}
		return nGemas;
	}

	private int heuristicaGema(PlayerObservation avatar, Observation gema) {
		int piedraEncima =  hayPiedra(gema.getX(), gema.getY() - 1) ? HAYPIEDRA : 0;
		int gemasCercas = HAYGEMAS * nGemasCerca(gema.getX(), gema.getY());
		// int enemigosCerca = HAYENEMIGOS * enemigoCerca(new Node(new Vector2d(gema.getX(), gema.getY())));
		int enemigosArea = HAYENEMIGOS * enemigosArea(gema.getX(), gema.getY());
		//int enemigosArea = 0;
		Vector2d aPos = new Vector2d(avatar.getX(), avatar.getY());
		Vector2d gPos = new Vector2d(gema.getX(), gema.getY());
		ArrayList<Node> camino = pF.getPath(aPos, gPos);
		int noAccesible = (camino == null || camino.isEmpty()) ? GEMINACCESIBLE : (caminoConPiedras(camino) ? CAMINOPIEDRAS : 0);
		int distancia = (camino == null || camino.isEmpty()) ? gema.getManhattanDistance(avatar) : camino.size();
		//System.out.println(enemigosArea);
		return distancia + piedraEncima + gemasCercas + noAccesible + enemigosArea;
	}



	// Ordena la lista de gemas en función de la heuristica impuesta
	private void ordenarGemas(PlayerObservation avatar, ArrayList<Observation> gemList) {

		Collections.sort(gemList, (gema1, gema2) -> {
			int h1 = heuristicaGema(avatar, gema1);
			int h2 = heuristicaGema(avatar, gema2);
			if (h1 < h2)
				return -1;
			else if (h1 == h2)
				return 0;
			else
				return 1;
		});
	}

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

	private void ordenarPiedras(PlayerObservation avatar, ArrayList<Observation> piedras) {
		Collections.sort(piedras, (p1, p2) -> {
			int d1 = p1.getManhattanDistance(avatar);
			int d2 = p2.getManhattanDistance(avatar);
			if (d1 < d2)
				return -1;
			else if (d1 == d2)
				return 0;
			else
				return 1;
		});
	}

}
