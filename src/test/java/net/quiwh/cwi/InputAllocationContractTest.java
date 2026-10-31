package net.quiwh.cwi;

import java.util.Arrays;
import net.quiwh.cwi.machine.InputAllocation;

public final class InputAllocationContractTest {
    public static void main(String[] args) {
        require(InputAllocation.allocate(new int[]{1},new int[]{1,1},new boolean[][]{{true},{true}})==null,"One item cannot satisfy two inputs");
        require(Arrays.equals(InputAllocation.allocate(new int[]{1,1},new int[]{1,1},new boolean[][]{{true,true},{true,false}}),new int[]{1,1}),"Overlapping broad/narrow tags must backtrack");
        require(InputAllocation.allocate(new int[]{800,400},new int[]{1000},new boolean[][]{{true,true}})[0]==800,"Fluid amounts combine across tanks");
        require(InputAllocation.allocate(new int[]{800,400},new int[]{1300},new boolean[][]{{true,true}})==null,"Insufficient fluid refused");
        require(InputAllocation.allocate(new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE},new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE},new boolean[][]{{true,true},{true,true}})!=null,"Sum cannot overflow int");
        var detailed=InputAllocation.allocateDetailed(new int[]{1,1},new int[]{1,1},new boolean[][]{{true,true},{true,false}});
        require(detailed[0][1]==1 && detailed[1][0]==1,"Allocation retains per-input ownership for catalysts");
        require(InputAllocation.allocate(new int[]{0},new int[]{0},new boolean[][]{{true}})[0]==0,"Empty demand is safe");
        System.out.println("Input allocation contracts: 7 checks passed");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
