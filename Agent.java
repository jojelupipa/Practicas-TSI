package practica_busqueda;

import java.util.ArrayList;

import core.game.StateObservation;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.pathfinder.PathFinder;

public class Agent extends BaseAgent {
	final static int requiredGems = 10;
	private boolean planExists;
	private PathFinder pf;
	

	public Agent(StateObservation so, ElapsedCpuTimer elapsedTimer) {
		super(so, elapsedTimer);
		//printObservationGrid(getObservationGrid(so));
		planExists = false;
		// Define obstacles
		// Initialize pathfinder
		// Run pathfinder
		
	}

	@Override
	public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		
		//Calculate current number of gems
        int nGems = 0;
        if(stateObs.getAvatarResources().isEmpty() != true){
            nGems = stateObs.getAvatarResources().get(6);
        }
        
        if(planExists) {
        	//TODO: Execute plan
        } else {
        	if(nGems < requiredGems) {
            	//TODO: thinks
        		
            } else {
        		//TODO: Find a (safe) way to exit
        	}
        }
		
		return Types.ACTIONS.ACTION_NIL;
	}
	
	private void printObservationGrid(ArrayList<Observation>[][] observationGrid ) {
		for(int i = 0; i < observationGrid.length; i++) {
			for(int j = 0; j < observationGrid[0].length; j++) {
				System.out.print(observationGrid[i][j] + " ");
			}
			System.out.println();
		}
	}

}
