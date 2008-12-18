/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Oct 23, 2008
 */
package org.jsoar.debugger;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.flexdock.docking.DockingConstants;
import org.jdesktop.swingx.JXLabel;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import org.jdesktop.swingx.autocomplete.ObjectToStringConverter;
import org.jsoar.kernel.Production;
import org.jsoar.kernel.tracing.Printer;
import org.jsoar.tcl.SoarTclException;
import org.jsoar.tcl.SoarTclInterface;
import org.jsoar.util.SwingTools;
import org.jsoar.util.adaptables.Adaptables;

import com.google.common.collect.ForwardingList;

/**
 * @author ray
 */
public class ProductionEditView extends AbstractAdaptableView
{
    private static final long serialVersionUID = -5150761314645770374L;

    private final JSoarDebugger debugger;
    private final JTextArea textArea = new JTextArea("Double-click a production (or right-click) to edit, or just start typing.");
    private final JXLabel status = new JXLabel("Ready");
    private final AbstractAction loadAction = new AbstractAction("Load [Ctrl-Return]") {

        private static final long serialVersionUID = -199488933120052983L;

        @Override
        public void actionPerformed(ActionEvent arg0)
        {
            load();
        }};

    /**
     * Wrapper list that forwards to the production model in the list view.
     * This is the list we use for auto-complete.
     * 
     * TODO: There are probably some synchronization issues here.
     */
    private final ForwardingList<Production> productions = new ForwardingList<Production>() {

        @Override
        protected List<Production> delegate()
        {
            ProductionTableModel model = Adaptables.adapt(debugger, ProductionTableModel.class);
            if(model == null)
            {
                return new ArrayList<Production>();
            }
            return model.getProductions();
        }};
    
    public ProductionEditView(JSoarDebugger debugger)
    {
        super("productionEditor", "Production Editor");
        
        this.debugger = debugger;
        
        addAction(DockingConstants.PIN_ACTION);
        
        JPanel p = new JPanel(new BorderLayout());
        SwingTools.addUndoSupport(textArea);
        p.add(new JScrollPane(textArea), BorderLayout.CENTER);
        
        JPanel north = new JPanel(new BorderLayout());
        north.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        final JTextField productionField = new JTextField("Enter production name here and hit enter");
        // Edit the production when they hit enter
        productionField.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                editProduction(productionField.getText().trim());
            }});
        north.add(productionField, BorderLayout.CENTER);
        
        // Set up auto completion...
        // TODO: get new swingx with fix for exception on double-click:
        // https://swingx.dev.java.net/issues/show_bug.cgi?id=943
        AutoCompleteDecorator.decorate(productionField, productions, true, new ObjectToStringConverter() {

            @Override
            public String getPreferredStringForItem(Object o)
            {
                return o != null ? ((Production) o).getName().getValue() : null;
            }});
        
        p.add(north, BorderLayout.NORTH);
        
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        status.setLineWrap(true);
        
        south.add(status, BorderLayout.CENTER);
        final JButton loadButton = new JButton(loadAction);
        // map ctrl+return to load the production back into the agent.
        textArea.getKeymap().addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_MASK), loadAction);
        
        JPanel t = new JPanel();
        t.add(loadButton);
        south.add(t, BorderLayout.EAST);
        
        p.add(south, BorderLayout.SOUTH);
        
        setContentPane(p);
    }
    
    /**
     * Tell the view to start editing the named production. The production's
     * code is loaded into the editor and the view is brought to the front.
     * 
     * @param name The name of the production to edit.
     */
    public void editProduction(String name)
    {
        final String productionText = getProductionText(name);
        textArea.setText(productionText);
        status.setText(productionText.length() != 0 ? "Editing production '" + name + "'" : "No production '" + name + "'");
        this.setActive(true);
    }
    
    private void load()
    {
        final String contents = textArea.getText().trim();
        if(contents.length() == 0)
        {
            return;
        }
        
        final SoarTclInterface ifc = debugger.getTcl();
        
        final String result = debugger.getAgentProxy().execute(new Callable<String>() {

            @Override
            public String call()
            {
                try
                {
                    ifc.eval(contents);
                    return "Loaded";
                }
                catch (SoarTclException e)
                {
                    return "ERROR: " + e.getMessage();
                }
            }});
        
        status.setText(result);
    }

    private String getProductionText(final String name)
    {
        return debugger.getAgentProxy().execute(new Callable<String>() {

            public String call() throws Exception
            {
                final Production p = debugger.getAgentProxy().getAgent().getProductions().getProduction(name);
                if(p != null)
                {
                    StringWriter s = new StringWriter();
                    p.print(new Printer(s, true), false);
                    return s.toString();
                }
                return "";
            }});
    }
}
