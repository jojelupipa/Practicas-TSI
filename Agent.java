package practica_busqueda;

import java.util.ArrayList;
import java.util.Vector;

import core.game.StateObservation;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.util.Collections;




public class Agent extends BaseAgent {
	private final static int REQUIREDGEMS = 10;
	private PathFinder pF;
	private ArrayList<Node> path = new ArrayList<>();
	private PlayerObservation ultPos;
	private ArrayList<Observation> grid[][];
	private final static int HAYPIEDRA = 2;
	private final static int HAYGEMAS = -1;
	private final static int GEMINACCESIBLE = 1000;
	private final static int DISTANCIA_PANICO = 2;
	private int nQuieto;
	

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

		if ((avatar.getX() != ultPos.getX() || avatar.getY() != ultPos.getY()) && !path.isEmpty())
			path.remove(0);
		else
			++nQuieto;

		// Elemento reactivo: Si encontramos un obstáculo no mortal, recalcular ruta
		
		if (nQuieto > 4) {
			path.clear();
			pF.run(stateObs);
			//System.out.println("Me he bloqueado");
		}
		

		if(!path.isEmpty()) {
        	// Ejecuto el plan
			Node siguientePos = path.get(0);
			//System.out.println(siguientePos.position.x + ", " + siguientePos.position.y);

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


        } else {
        	if(nGems < REQUIREDGEMS) {
        		// Obtengo la lista de gemas ordenadas por la distancia hacia el jugador y las ordenamos con nuestra heurística
        		ArrayList<Observation> gemList = getGemsList(stateObs);
        		for (Observation gema : gemList) {
					Vector2d gPos = new Vector2d(gema.getX(), gema.getY());
					findPath(aPos, gPos);
				}

        		//for (Observation gema : gemList)
        		//	System.out.println(gema);
        		ordenarGemas(avatar, gemList);
        		//System.out.println(gemList);
        		// Buscamos la primera gema más prometedora con camino accesible
				Observation gema = gemList.get(0);
				//System.out.println(gema);
				Vector2d gPos = new Vector2d(gema.getX(), gema.getY());
				int id = getID(aPos) * 10000 + getID(gPos);
				path = findPath(aPos, gPos);
				
				if (path != null) 				 // Imprimimos el camino
					pF.astar.printPath(id, path);
				
            } else {
        		// Obtengo la posición del portal más cercano y obtengo la ruta
				// TODO: mejorar el criterio de busqueda (añadir heuristica)
				Observation portal = getExit(stateObs);
				Vector2d pPos = new Vector2d(portal.getX(), portal.getY());
				path = findPath(aPos, pPos);
			}
        	nQuieto = 0;
        }
		ultPos = avatar;
		//System.out.println(nextAction);
		
		// 	Elemento reactivo: Si nos acercamos a un enemigo, recalcular ruta
		ArrayList<Observation>[] enemigos= getEnemiesList(stateObs);
		
		for (int i = 0; i < enemigos.length; i++) {
			for (Observation enemigo_actual : enemigos[i]){
				if (avatar.getManhattanDistance(enemigo_actual) <= DISTANCIA_PANICO) {
					path.clear();
					pF.run(stateObs);
					System.out.println("Panico en las calles");
					nextAction = bestScapingAction(avatar, enemigo_actual);
				}
			}
		}		
		
		
		
		try{Thread.sleep(50);}catch(InterruptedException e){System.out.println(e);}
		return nextAction;
	}

	
	private ACTIONS bestScapingAction(PlayerObservation avatar, Observation enemigo) {
		Types.ACTIONS nextAction = Types.ACTIONS.ACTION_NIL;
		if(enemigo.getX() > avatar.getX()) {
			nextAction = Types.ACTIONS.ACTION_LEFT;
		} else if (enemigo.getX() < avatar.getX()) {
			nextAction = Types.ACTIONS.ACTION_RIGHT;
		} else if (enemigo.getY() > avatar.getY()) {
			nextAction = Types.ACTIONS.ACTION_DOWN;
		} else if (enemigo.getY() < avatar.getY()) {
			nextAction = Types.ACTIONS.ACTION_UP;
		}		
		return nextAction;
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
		for (Observation obs : grid[x][y]) {
			if (obs.getType() == ObservationType.BOULDER)
				return true;
		}
		return false;
	}

	private int getID(Vector2d punto) {
		return ((int) punto.x) * 100 + ((int) punto.y);
	}

	private void recalcularCamino(Vector2d origen, Vector2d destino) {
		int id = getID(origen) * 10000 + getID(destino);
		pF.astar.deletePath(id);
		pF.astar.findPath(new Node(origen), new Node(destino));
	}

	private ArrayList<Node> findPath(Vector2d origen, Vector2d destino) {
		return pF.astar.findPath(new Node(origen), new Node(destino));
	}

	private boolean caminoConPiedras(ArrayList<Node> camino) {
		for (Node nodo : camino) {
			for (Observation obs : grid[(int) nodo.position.x][(int) nodo.position.y]) {
				if (obs.getType() == ObservationType.BOULDER)
					return true;
			}
		}
		return false;
	}

	private int nGemasCerca(int x, int y) {
		int nGemas = 0;
		int[][] direcciones = {{1, 0}, {0, 1}, {1, 1}, {-1, 0}, {0, -1}, {-1, -1}, {1, -1}, {-1, 1}};
		for (int[] dir : direcciones) {
			for (Observation obs : grid[x + dir[0]][y + dir[1]]) {
				if (obs.getType() == ObservationType.GEM)
					++nGemas;
			}
		}
		return nGemas;
	}

	private int heuristicaGema(PlayerObservation avatar, Observation gema) {
		int distancia = gema.getManhattanDistance(avatar);
		int piedraEncima =  hayPiedra(gema.getX(), gema.getY() - 1) ? HAYPIEDRA : 0;
		int gemasCercas = HAYGEMAS * nGemasCerca(gema.getX(), gema.getY());
		Vector2d aPos = new Vector2d(avatar.getX(), avatar.getY());
		Vector2d gPos = new Vector2d(gema.getX(), gema.getY());
		ArrayList<Node> camino = pF.getPath(aPos, gPos);
		int noAccesible = (camino == null || camino.isEmpty()) ? GEMINACCESIBLE : 0;
		return distancia + piedraEncima + gemasCercas + noAccesible;
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

}
