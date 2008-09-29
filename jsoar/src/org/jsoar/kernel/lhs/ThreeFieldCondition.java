/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 16, 2008
 */
package org.jsoar.kernel.lhs;

import java.util.LinkedList;

import org.jsoar.kernel.symbols.Variable;

/**
 * @author ray
 */
public abstract class ThreeFieldCondition extends Condition
{
    public Test id_test;
    public Test attr_test;
    public Test value_test;
    
    protected ThreeFieldCondition()
    {
        
    }
    
    /**
     * Copy constructor used to negate pos and neg conditions. Test fields are
     * only a shallow copy!
     */
    protected ThreeFieldCondition(ThreeFieldCondition other)
    {
        this.id_test = other.id_test;
        this.attr_test = other.attr_test;
        this.value_test = other.value_test;
    }
    
    /* (non-Javadoc)
     * @see org.jsoar.kernel.Condition#asThreeFieldCondition()
     */
    @Override
    public ThreeFieldCondition asThreeFieldCondition()
    {
        return this;
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.Condition#addAllVariables(int, java.util.List)
     */
    @Override
    public void addAllVariables(int tc_number, LinkedList<Variable> var_list)
    {
        if(id_test != null)
        {
            id_test.addAllVariables(tc_number, var_list);
        }
        if(attr_test != null)
        {
            attr_test.addAllVariables(tc_number, var_list);
        }
        if(value_test != null)
        {
            value_test.addAllVariables(tc_number, var_list);
        }
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.lhs.Condition#cond_is_in_tc(int)
     */
    @Override
    public boolean cond_is_in_tc(int tc)
    {
        return TestTools.test_is_in_tc(id_test, tc);
    }
}
