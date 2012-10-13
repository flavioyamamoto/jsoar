/*
 * Copyright (c) 2009 Dave Ray <daveray@gmail.com>
 *
 * Created on Jul 28, 2009
 */
package org.jsoar.kernel.rete;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.Production;
import org.jsoar.kernel.Production.Support;
import org.jsoar.kernel.ProductionManager;
import org.jsoar.kernel.ProductionType;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.events.ProductionAddedEvent;
import org.jsoar.kernel.learning.rl.ReinforcementLearning;
import org.jsoar.kernel.memory.PreferenceType;
import org.jsoar.kernel.rhs.Action;
import org.jsoar.kernel.rhs.ActionSupport;
import org.jsoar.kernel.rhs.FunctionAction;
import org.jsoar.kernel.rhs.MakeAction;
import org.jsoar.kernel.rhs.ReteLocation;
import org.jsoar.kernel.rhs.RhsFunctionCall;
import org.jsoar.kernel.rhs.RhsSymbolValue;
import org.jsoar.kernel.rhs.RhsValue;
import org.jsoar.kernel.rhs.UnboundVariable;
import org.jsoar.kernel.symbols.DoubleSymbol;
import org.jsoar.kernel.symbols.IntegerSymbol;
import org.jsoar.kernel.symbols.StringSymbol;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.SymbolFactoryImpl;
import org.jsoar.kernel.symbols.SymbolImpl;
import org.jsoar.kernel.symbols.Variable;
import org.jsoar.util.Arguments;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.properties.PropertyKey;
import org.jsoar.util.properties.PropertyManager;

/**
 * @author ray
 */
public class ReteNetReader
{
    public static final String MAGIC_STRING = "JSoarCompactReteNet";
    public static final int FORMAT_VERSION = 2;
    public static final int COMPRESSION_TYPE = 1;

    private final Agent context;
    private final SymbolFactoryImpl syms;
    private final Rete rete;
    private final ProductionManager productionManager;
    private final ReinforcementLearning rl;

    private List<Symbol> symbolMap;
    private List<AlphaMemory> alphaMemories;

    protected ReteNetReader(Agent context)
    {
        Arguments.checkNotNull(context, "context");
        this.context = context;
        this.syms = Adaptables.require(getClass(), context, SymbolFactoryImpl.class);
        this.rete = Adaptables.require(getClass(), context, Rete.class);
        this.rl = Adaptables.require(getClass(), context, ReinforcementLearning.class);
        this.productionManager = context.getProductions();
    }

    /**
     * Read a rete network from the given input stream. The agent
     * will be reinitialized, all productions will be excised and then
     * the rete network will be loaded. 
     * 
     * <p>This method does not close the input stream
     * 
     * @param is the input stream to read from
     * @throws IOException
     * @throws SoarException if an error occurs
     */
    public void read(InputStream is) throws IOException, SoarException
    {
        DataInputStream dis = new DataInputStream(is);
        final String magic = dis.readUTF();
        if(!MAGIC_STRING.equals(magic))
        {
            throw new SoarException("Input does not appear to be a valid JSoar rete net");
        }
        final int version = dis.readInt();
        final int compression = dis.readInt();
        if(version != FORMAT_VERSION)
        {
            throw new SoarException(String.format("Unsupported JSoar rete net version. Expected %d, got %d", FORMAT_VERSION, version));
        }
        if(compression != COMPRESSION_TYPE)
        {
            throw new SoarException(String.format("Unsupported JSoar rete net compression type. Expected %d, got %d", COMPRESSION_TYPE, compression));
        }

        // Everything else is compressed.
        dis = new DataInputStream(new GZIPInputStream(is));
        readAllSymbols(dis);
        readAlphaMemories(dis);
        readBetaMemories(dis);
        readProperties(dis);
    }

    @SuppressWarnings("unchecked")
    private void readProperties(DataInputStream dis) throws IOException, SoarException
    {
        int numProperties = dis.readInt();
        PropertyManager properties = context.getProperties();
        for(int i = 0; i < numProperties; i++)
        {
            String name = dis.readUTF();
            PropertyKey<?> propertyKey = properties.getKey(name);
            if (propertyKey == null)
            {
                throw new SoarException("Unknown property " + name);
            }

            if (propertyKey.getType().equals(Boolean.class))
            {
                boolean value = dis.readBoolean();
                properties.set((PropertyKey<Boolean>)propertyKey, value);
            }
            else if (propertyKey.getType().equals(Integer.class))
            {
                int value = dis.readInt();
                properties.set((PropertyKey<Integer>)propertyKey, value);
            }
            else
            {
                throw new SoarException(String.format("Unhandled property type \"%s\" for property %s.", propertyKey.getType(), name));
            }
        }
    }

    private void readBetaMemories(DataInputStream dis) throws IOException, SoarException
    {
        // Number of children.
        int numNodes = dis.readInt();

        for (int i = 0; i < numNodes; i++)
        {
            readNodeAndChildren(dis, rete.dummy_top_node);
        }
    }

    private void readNodeAndChildren(DataInputStream dis, ReteNode parent) throws IOException, SoarException
    {
        final ReteNodeType type = ReteNodeType.valueOf(dis.readUTF());
        ReteNode New = null;
        AlphaMemory am;
        boolean left_unlinked_flag;
        ReteTest other_tests;
        Production prod;

        /* 
         * Initializing the left_hash_loc structure to flag values.
         * It gets passed into some of the various make_new_??? functions
         * below but is never used (hopefully) for UNHASHED node types.
         */
        VarLocation left_hash_loc = new VarLocation(-1, -1);
        switch (type) {
        case MEMORY_BNODE:
            left_hash_loc = readLeftHashLoc(dis);
            // ... and fall through to the next case below ... 
        case UNHASHED_MEMORY_BNODE:
            New = ReteNode.make_new_mem_node(rete, parent, type, left_hash_loc);
            break;

        case MP_BNODE:
            left_hash_loc = readLeftHashLoc(dis);
            // ... and fall through to the next case below ... 
        case UNHASHED_MP_BNODE:
            am = alphaMemories.get(dis.readInt());
            am.reference_count++;
            other_tests = readTestList(dis);
            left_unlinked_flag = dis.readBoolean();
            New = ReteNode.make_new_mp_node(rete, parent, type, left_hash_loc, am, other_tests,
                    left_unlinked_flag);
            break;

        case POSITIVE_BNODE:
        case UNHASHED_POSITIVE_BNODE:
            am = alphaMemories.get(dis.readInt());
            am.reference_count++;
            other_tests = readTestList(dis);
            left_unlinked_flag = dis.readBoolean();
            New = ReteNode.make_new_positive_node(rete, parent, type, am, other_tests,
                    left_unlinked_flag);
            break;

        case NEGATIVE_BNODE:
            left_hash_loc = readLeftHashLoc(dis);
            // ... and fall through to the next case below ... 
        case UNHASHED_NEGATIVE_BNODE:
            am = alphaMemories.get(dis.readInt());
            am.reference_count++;

            other_tests = readTestList(dis);
            New = ReteNode.make_new_negative_node(rete, parent, type, left_hash_loc, am,other_tests);
            break;

        case CN_PARTNER_BNODE:
            int count = dis.readInt();
            ReteNode ncc_top = parent;
            while (count-- > 0) ncc_top = ncc_top.real_parent_node();
            New = ReteNode.make_new_cn_node(rete, ncc_top, parent);
            break;

        case P_BNODE:
            String name = dis.readUTF();
            String doc = dis.readUTF();
            ProductionType prodType = ProductionType.valueOf(dis.readUTF());
            Support declaredSupport = Support.valueOf(dis.readUTF());
            Action actionList = readActionList(dis);
            prod = Production.newBuilder()
                    .name(name)
                    .documentation(doc)
                    .type(prodType)
                    .support(declaredSupport)
                    .actions(actionList)
                    .build();

            int numUnboundVariables = dis.readInt();
            rete.update_max_rhs_unbound_variables(numUnboundVariables);
            List<Variable> unboundVars = new ArrayList<Variable>(numUnboundVariables);
            for(int i = 0; i < numUnboundVariables; i++)
            {
                unboundVars.add(getSymbol(dis.readInt()).asVariable());
            }
            prod.setRhsUnboundVariables(unboundVars);

            // Soar-RL stuff
            rl.addProduction(prod);

            New = ReteNode.make_new_production_node(rete, parent, prod);
            boolean hasNodeVariableNames = dis.readBoolean();
            if (hasNodeVariableNames)
            {
                New.b_p().parents_nvn = readNodeVarNames(dis, parent, symbolMap);
            }
            else
            {
                New.b_p().parents_nvn = null;
            }

            // --- call new node's add_left routine with all the parent's tokens --- 
            rete.update_node_with_matches_from_above(New);

            productionManager.addProductionToNameTypeMaps(prod);
            // --- invoke callback on the production --- 
            context.getEvents().fireEvent(new ProductionAddedEvent(context, prod));
            break;
        default:
            throw new SoarException("Unhandled ReteNodeType: " + type);
        }

        /* --- read in the children of the node --- */
        int count = dis.readInt();
        while (count-- > 0) 
        {
            readNodeAndChildren(dis, New);
        }
    }

    private VarLocation readLeftHashLoc(DataInputStream dis) throws IOException
    {
        int field_num = dis.readInt(); 
        int levels_up = dis.readInt();

        return new VarLocation(levels_up, field_num);
    }

    private Action readActionList(DataInputStream dis) throws IOException, SoarException
    {
        Action a;
        Action prev_a = null;
        Action first_a = null;
        int count; 

        count = dis.readInt();

        while (count-- > 0) {
            a = readRHSAction(dis);
            if (prev_a != null)
            {
                prev_a.next = a;
            }
            else
            {
                first_a = a;
            }
            prev_a = a;
        }
        if (prev_a != null)
        {
            prev_a.next = null; 
        }
        else
        {
            first_a = null;
        }
        return first_a; 
    }

    private Action readRHSAction(DataInputStream dis) throws IOException, SoarException
    {
        // JSoar's Action lacks a type field. These constants {0, 1} match MAKE_ACTION and FUNCALL_ACTION.
        int type = dis.readInt();
        Action a = null;
        if (type == 0)
        {
            a = new MakeAction();
        }
        else if (type == 1)
        {
            a = new FunctionAction(null);
        }
        else
        {
            throw new SoarException(String.format("Unknown Action type %d.", type));
        }

        boolean hasPreferenceType = dis.readBoolean();
        if (hasPreferenceType)
        {
            String preference_type = dis.readUTF();
            a.preference_type = PreferenceType.valueOf(preference_type);
        }
        else
        {
            a.preference_type = null;
        }
        a.support = ActionSupport.valueOf(dis.readUTF());

        // FUNCALL_ACTION
        if (type == 1)
        {
            FunctionAction fa = a.asFunctionAction();
            fa.call = reteload_rhs_value(dis).asFunctionCall();
        }
        // MAKE_ACTION
        else if (type == 0)
        {
            MakeAction ma = a.asMakeAction();
            ma.id = reteload_rhs_value(dis);
            ma.attr = reteload_rhs_value(dis);
            ma.value = reteload_rhs_value(dis);
            if (a.preference_type != null && a.preference_type.isBinary())
            {
                ma.referent = reteload_rhs_value(dis);
            }
            else
            {
                ma.referent = null;
            }
        }
        else
        {
            throw new SoarException(String.format("Unknown Action type %d.", type));
        }
        
        return a;
    }

    private RhsValue reteload_rhs_value(DataInputStream dis) throws IOException, SoarException
    {
        RhsValue rv = null;
        SymbolImpl sym;
        int field_num;
        int type;
        int levels_up;

        type = dis.readInt();
        switch (type) {
        case 0: // RhsSymbolValue
            sym = getSymbol(dis.readInt());
            rv = new RhsSymbolValue(sym);
            break;
        case 1: // RhsFunctionCall
            sym = getSymbol(dis.readInt());
            boolean isStandalone = dis.readBoolean();

            // We don't check if the function name exists in context.getRhsFunctions() in case it's a custom RHS function.
            RhsFunctionCall funCall = new RhsFunctionCall(sym.asString(), isStandalone);
            int count = dis.readInt();
            while (count-- > 0)
            {
                funCall.addArgument(reteload_rhs_value(dis));
            }
            rv = funCall;
            break;
        case 2: // ReteLocation
            field_num = dis.readInt();
            levels_up = dis.readInt();
            rv = ReteLocation.create(field_num, levels_up);
            break;
        case 3: // UnboundVariable
            int i = dis.readInt(); // Index of the unbound variable.
            rete.update_max_rhs_unbound_variables(i+1);
            rv = UnboundVariable.create(i);
            break;
        default:
            throw new SoarException("Unhandled RHS type: " + type);
        }

        return rv;
    }

    private ReteTest readTestList(DataInputStream dis) throws IOException, SoarException
    {  
        ReteTest rt, prev_rt, first;
        int count;

        prev_rt = null;
        first = null;
        count = dis.readInt();
        while (count-- > 0)
        {
            rt = readTest(dis);
            if (prev_rt != null)
            {
                prev_rt.next = rt; 
            }
            else
            {
                first = rt;
            }
            prev_rt = rt;
        }

        if (prev_rt != null)
        {
            prev_rt.next = null;
        }
        else
        {
            first = null;
        }

        return first;
    }
    private ReteTest readTest(DataInputStream dis) throws IOException, SoarException
    {
        SymbolImpl sym;

        int type = dis.readInt();
        int right_field_num = dis.readInt();

        ReteTest rt = new ReteTest(type);
        if (rt.test_is_constant_relational_test())
        {
            type -= ReteTest.CONSTANT_RELATIONAL; // ReteTest's constructor will add this back in.
            sym = getSymbol(dis.readInt());
            rt = ReteTest.createConstantTest(type, right_field_num, (SymbolImpl)sym);
        }
        else if (rt.test_is_variable_relational_test()) {
            type -= ReteTest.VARIABLE_RELATIONAL; // ReteTest's constructor will add this back in.
            int field_num = dis.readInt();
            int levels_up = dis.readInt();
            rt = ReteTest.createVariableTest(type, right_field_num, new VarLocation(levels_up, field_num));
        }
        else if (type == ReteTest.DISJUNCTION) {
            int count = dis.readInt();
            List<SymbolImpl> disjuncts = new ArrayList<SymbolImpl>(count);

            while (count-- > 0) {
                sym = getSymbol(dis.readInt());
                disjuncts.add((SymbolImpl)sym);
            }
            rt = ReteTest.createDisjunctionTest(right_field_num, disjuncts);
        }
        else if (type == ReteTest.ID_IS_GOAL)
        {
            rt = ReteTest.createGoalIdTest(); 
        }
        else if (type == ReteTest.ID_IS_IMPASSE)
        {
            rt = ReteTest.createImpasseIdTest();
        }
        else
        {
            throw new SoarException("Unknown test type: " + rt + " (" + type + ")");
        }

        return rt;
    }

    private static interface SymbolReader<T extends Symbol>
    {
        T read(DataInputStream dis) throws IOException;
    }

    /**
     * Read all symbols, indexed by their rete-net symbol table index.
     * 
     * @param dis
     * @return
     * @throws IOException 
     * @throws SoarException 
     * @see ReteNetWriter#writeAllSymbols
     */
    private void readAllSymbols(DataInputStream dis) throws IOException, SoarException
    {
        final List<Symbol> result = new ArrayList<Symbol>();
        result.add(null); // symbol 0 is null (see writeAllSymbols)

        result.addAll(readSymbolList(dis, new SymbolReader<StringSymbol>(){

            public StringSymbol read(DataInputStream dis) throws IOException
            {
                return syms.createString(dis.readUTF());
            }}));
        result.addAll(readSymbolList(dis, new SymbolReader<Variable>(){

            public Variable read(DataInputStream dis) throws IOException
            {
                return syms.make_variable(dis.readUTF());
            }}));
        result.addAll(readSymbolList(dis, new SymbolReader<IntegerSymbol>(){

            public IntegerSymbol read(DataInputStream dis) throws IOException
            {
                return syms.createInteger(dis.readLong());
            }}));
        result.addAll(readSymbolList(dis, new SymbolReader<DoubleSymbol>(){

            public DoubleSymbol read(DataInputStream dis) throws IOException
            {
                return syms.createDouble(dis.readDouble());
            }}));
        this.symbolMap = result;
    }

    private <T extends Symbol> List<T> readSymbolList(DataInputStream dis, SymbolReader<T> reader) throws IOException, SoarException
    {
        final int size = dis.readInt();
        if(size < 0)
        {
            throw new SoarException(String.format("Invalid symbol list size %d", size));
        }
        final List<T> result = new ArrayList<T>(size);
        for(int i = 0; i < size; ++i)
        {
            result.add(reader.read(dis));
        }
        return result;
    }

    private SymbolImpl getSymbol(int index) throws SoarException
    {
        if(index < 0 || index >= symbolMap.size())
        {
            throw new SoarException(String.format("Invalid symbol index %d", index));
        }
        return (SymbolImpl) symbolMap.get(index);
    }

    private void readAlphaMemories(DataInputStream dis) throws IOException, SoarException
    {
        final int count = dis.readInt();
        if(count < 0)
        {
            throw new SoarException(String.format("Invalid alpha memory list size %d", count));
        }

        final List<AlphaMemory> ams = new ArrayList<AlphaMemory>(count);
        ams.add(null); // am index values start at 1. See writeAlphaMemories
        for(int i = 0; i < count; ++i)
        {
            final SymbolImpl id = getSymbol(dis.readInt());
            final SymbolImpl attr = getSymbol(dis.readInt());
            final SymbolImpl value = getSymbol(dis.readInt());
            final boolean acceptable = dis.readBoolean();
            ams.add(rete.find_or_make_alpha_mem(id, attr, value, acceptable));
        }
        this.alphaMemories = ams;
    }

    private Object readVarNames(DataInputStream dis) throws SoarException, IOException
    {
        final byte type = dis.readByte();
        if(type == 0)
        {
            return null;
        }
        else if(type == 1)
        {
            final int index = dis.readInt(); 
            return VarNames.one_var_to_varnames(getSymbol(index).asVariable());
        }
        else if(type == 2)
        {
            final int count = dis.readInt();
            if(count < 0)
            {
                throw new SoarException(String.format("Count of varnames list record must be positive, got %d", count));
            }
            final LinkedList<Variable> vars = new LinkedList<Variable>();
            for(int i = 0; i < count; ++i)
            {
                vars.add(getSymbol(i).asVariable());
            }
            return VarNames.var_list_to_varnames(vars);
        }
        else
        {
            throw new SoarException(String.format("Invalid varnames record type. Expected 0, 1, or 2, got %d", type));
        }
    }

    private NodeVarNames readNodeVarNames(DataInputStream dis, ReteNode node, List<Symbol> symbolMap) throws SoarException, IOException 
    {
        if (node.node_type == ReteNodeType.DUMMY_TOP_BNODE)
        {
            return null;
        }
        if (node.node_type == ReteNodeType.CN_BNODE) 
        {
            ReteNode temp = node.b_cn().partner.parent;
            NodeVarNames nvn_for_ncc = readNodeVarNames(dis, temp, symbolMap);
            final NodeVarNames bottom_of_subconditions = nvn_for_ncc;
            while (temp != node.parent)
            {
                temp = temp.real_parent_node();
                nvn_for_ncc = nvn_for_ncc.parent;
            }
            return NodeVarNames.createForNcc(nvn_for_ncc,
                    bottom_of_subconditions);
        } 
        Object id = readVarNames(dis);
        Object attr = readVarNames(dis);
        Object value = readVarNames(dis);
        NodeVarNames parent = readNodeVarNames(dis, node.real_parent_node(), symbolMap);
        return NodeVarNames.newInstance(parent, id, attr, value);
    }
}
