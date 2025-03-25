package src.pas.pokemon.agents;

import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class TreeNode {
    private BattleView state;
    private MoveView lastMove;
    private double probability;
    private List<TreeNode> children; //future states

    public TreeNode(BattleView state, MoveView move, double probability) {
        this.state = state;
        this.lastMove = move;
        this.probability = probability;
        this.children = new ArrayList<>();
    }

    public BattleView getState() { return state; }
    public MoveView getMove() { return lastMove; }
    public double getProbability() { return probability; }
    public List<TreeNode> getChildren() { return children; }

    //expand all possible moves
    public void expand(int teamIdx) {
        List<MoveView> legalMoves = Arrays.asList(state.getTeamView(teamIdx).getPokemonView(0).getMoveViews());

        for (MoveView move : legalMoves) {
            List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(state, 0, 0);

            for (Pair<Double, BattleView> outcome : outcomes) {
                double prob = outcome.getFirst();
                BattleView nextState = outcome.getSecond();
                children.add(new TreeNode(nextState, move, prob));
            }
        }
    }

    //checks if either the enemy loses or we lose
    public boolean isTerminal() {
        return state.getTeamView(0).size() == 0 || state.getTeamView(1).size() == 0;
    }
}
