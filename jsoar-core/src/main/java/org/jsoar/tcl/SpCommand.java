package org.jsoar.tcl;

import org.jsoar.kernel.parser.ParserException;
import org.jsoar.kernel.rhs.ReordererException;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/**
 * @author ray
 */
final class SpCommand implements Command
{
    /**
     * 
     */
    private final SoarTclInterface ifc;

    /**
     * @param soarTclInterface
     */
    SpCommand(SoarTclInterface soarTclInterface)
    {
        ifc = soarTclInterface;
    }

    @Override
    public void cmdProc(Interp interp, TclObject[] args) throws TclException
    {
        if(args.length != 2)
        {
            throw new TclNumArgsException(interp, 0, args, "body");
        }
        
        try
        {
            ifc.getAgent().getProductions().loadProduction(args[1].toString());
        }
        catch (ReordererException e)
        {
            throw new TclException(interp, e.getMessage());
        }
        catch (ParserException e)
        {
            throw new TclException(interp, e.getMessage());
        }
    }
}