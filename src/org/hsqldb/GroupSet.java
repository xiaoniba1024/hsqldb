package org.hsqldb;

import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.IntKeyIntValueHashMap;
import org.hsqldb.lib.Iterator;

import java.util.Arrays;

public class GroupSet {
    public Expression[] query;
    public HsqlArrayList sets = new HsqlArrayList();
    public int nullSets = 0;

    private IntKeyIntValueHashMap columnIndexMap = new IntKeyIntValueHashMap();

    public GroupSet(Expression[] expressions) {
        query = expressions;
    }
    public Iterator getIterator(){
        return sets.iterator();
    }
    private int getColumnIndex(Expression e){
        int aliasCode = e.getAlias().hashCode();
        if (aliasCode == 0){
            aliasCode = e.getSQL().hashCode();
        }
        int colIndex = columnIndexMap.get(aliasCode, -1);
        return colIndex;
    }
    public int isGrouped(HsqlArrayList current, Expression e){
        int count = 0;
        if (current == null){
            return (1<<(e.nodes.length)) - 1; //Total Aggregate case
        }
        for (int i = 0;i<e.nodes.length; i++){
            count = count<<1;
            int colIndex = columnIndexMap.get(e.nodes[i].getAlias().hashCode());
            if (!current.contains(colIndex)){
                count += 1;
            }
        }
        return count;
    }
    public void process(Expression[] e, int indexLimitVisible, int indexStartHaving){
        for (int i=indexLimitVisible; i<indexStartHaving;i++){
            columnIndexMap.put(e[i].getSQL().hashCode(), e[i].resultTableColumnIndex);
            columnIndexMap.put(e[i].getColumnName().hashCode(), e[i].resultTableColumnIndex);
            columnIndexMap.put(e[i].getAlias().hashCode(), e[i].resultTableColumnIndex);
        }

        for (int i=0;i<indexLimitVisible;i++){
            int colIndex = columnIndexMap.get(e[i].getAlias().hashCode(), -1);
            if (colIndex == -1){
                colIndex = columnIndexMap.get(e[i].getColumnName().hashCode(), -1);
            }
            if (colIndex == -1 && e[i].getClass() != ExpressionColumn.class){
                colIndex = columnIndexMap.get(e[i].getSQL().hashCode(), -1);
            }
            if (colIndex != -1){
                e[i].resultTableColumnIndex = colIndex;
            }
        }

        HsqlArrayList tmp = evaluate(query);
        Iterator it = tmp.iterator();
        while(it.hasNext()){
            HsqlArrayList set = (HsqlArrayList) it.next();
            if (set.isEmpty()){
                nullSets++;
            } else{
                sets.add(set);
            }
        }
    }

    private HsqlArrayList evaluate(Expression e) {
        if (e.opType == OpTypes.NONE){
            HsqlArrayList sets = new HsqlArrayList();
            sets.add(new HsqlArrayList());
            return sets;
        }
        Expression[] exprs = e.nodes;

        if (exprs.length == 0 || (e.opType != OpTypes.VALUELIST && e.opType != OpTypes.ROW)){
            exprs = new Expression[1];
            exprs[0] = e;
        }
        switch (e.groupingType) {
            case Tokens.CUBE:
                return powerSet(exprs);
            case Tokens.ROLLUP:
                return rollUp(exprs);
            case Tokens.SETS:
                return grouping(exprs);
            default:
                if (e.nodes.length == 0){
                    HsqlArrayList sets = new HsqlArrayList();
                    HsqlArrayList inner = new HsqlArrayList();
                    inner.add(getColumnIndex(e));
                    sets.add(inner);
                    return sets;
                }
                return evaluate(e.nodes);
        }
    }
    private HsqlArrayList evaluate(Expression[] e) {
        HsqlArrayList sets = new HsqlArrayList();
        if (e.length == 0){
            HsqlArrayList inner = new HsqlArrayList();
            sets.add(inner);
            return sets;
        }
        HsqlArrayList first = evaluate(e[0]);
        HsqlArrayList results = evaluate(Arrays.copyOfRange(e, 1, e.length));
        Iterator it = results.iterator();
        while (it.hasNext()){
            HsqlArrayList current = (HsqlArrayList) it.next();
            Iterator itFirst = first.iterator();
            while (itFirst.hasNext()){
                HsqlArrayList newSet = new HsqlArrayList();
                HsqlArrayList next = (HsqlArrayList) itFirst.next();
                newSet.addAll(next);
                newSet.addAll(current);
                sets.add(newSet);
            }
        }
        return sets;
    }
    private HsqlArrayList powerSet(Expression[] expressions){
        HsqlArrayList sets = new HsqlArrayList();
        if (expressions.length == 0){
            sets.add(new HsqlArrayList());
            return sets;
        }
        HsqlArrayList first;
        if (expressions[0].nodes.length !=0 &&
                (expressions[0].opType == OpTypes.VALUELIST || expressions[0].opType == OpTypes.ROW)){
            first = evaluate(expressions[0]);
        } else {
            first = new HsqlArrayList();
            HsqlArrayList tmp = new HsqlArrayList();
            tmp.add(getColumnIndex(expressions[0]));
            first.add(tmp);
        }
        HsqlArrayList results = powerSet(Arrays.copyOfRange(expressions, 1, expressions.length));
        Iterator itFirst = first.iterator();
        while (itFirst.hasNext()){
            HsqlArrayList current = (HsqlArrayList) itFirst.next();
            Iterator it = results.iterator();
            while (it.hasNext()) {
                HsqlArrayList newSet = new HsqlArrayList();
                HsqlArrayList next = (HsqlArrayList) it.next();
                newSet.addAll(current);
                newSet.addAll(next);
                if (!newSet.isEmpty()) {
                    sets.add(newSet);
                }
            }
        }
        sets.addAll(results);
        return sets;
    }
    private HsqlArrayList rollUp(Expression[] expressions){
        HsqlArrayList sets = new HsqlArrayList();
        if (expressions.length == 0){
            sets.add(new HsqlArrayList());
            return sets;
        }
        HsqlArrayList first;

        if (expressions[0].nodes.length !=0  &&
                (expressions[0].opType == OpTypes.VALUELIST || expressions[0].opType == OpTypes.ROW)){
            first = evaluate(expressions[0]);
        } else {
            first = new HsqlArrayList();
            HsqlArrayList tmp = new HsqlArrayList();
            tmp.add(getColumnIndex(expressions[0]));
            first.add(tmp);
        }
        HsqlArrayList results = rollUp(Arrays.copyOfRange(expressions, 1, expressions.length));
        Iterator it = results.iterator();
        while (it.hasNext()){
            HsqlArrayList current = (HsqlArrayList) it.next();
            Iterator itFirst = first.iterator();
            while (itFirst.hasNext()){
                HsqlArrayList next = (HsqlArrayList) itFirst.next();
                HsqlArrayList newSet = new HsqlArrayList();
                newSet.addAll(next);
                newSet.addAll(current);
                sets.add(newSet);
            }
        }
        sets.add(new HsqlArrayList());
        return sets;
    }
    private HsqlArrayList grouping(Expression[] expressions){
        HsqlArrayList sets = new HsqlArrayList();
        if (expressions.length == 0){
            return sets;
        }
        if (expressions[0].nodes.length !=0 &&
                (expressions[0].opType == OpTypes.VALUELIST || expressions[0].opType == OpTypes.ROW)){
            sets = evaluate(expressions[0]);
        } else {
            sets = new HsqlArrayList();
            HsqlArrayList tmp = new HsqlArrayList();
            if (expressions[0].opType != OpTypes.NONE){
                tmp.add(getColumnIndex(expressions[0]));
            }
            sets.add(tmp);
        }
        HsqlArrayList results = grouping(Arrays.copyOfRange(expressions, 1, expressions.length));
        sets.addAll(results);
        return sets;
    }
}
