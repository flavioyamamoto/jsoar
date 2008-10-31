/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Oct 30, 2008
 */
package org.jsoar.debugger;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.concurrent.Callable;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jsoar.kernel.Agent;
import org.jsoar.util.ByRef;

/**
 * @author ray
 */
public class StatusBar extends JPanel
{
    private static final long serialVersionUID = 1501760828755152573L;

    private final LittleDebugger debugger;
    private final JLabel runState = new JLabel("run state");
    private final JLabel phase = new JLabel("phase");
    private final JLabel settings = new JLabel("Status");
    
    public StatusBar(LittleDebugger debugger)
    {
        super(new BorderLayout());
        
        this.debugger = debugger;
        
        runState.setBorder(BorderFactory.createEtchedBorder());
        runState.setPreferredSize(new Dimension(100, 25));
        phase.setBorder(BorderFactory.createEtchedBorder());
        phase.setPreferredSize(new Dimension(100, 25));
        phase.setMaximumSize(new Dimension(100, 25));
        settings.setBorder(BorderFactory.createEtchedBorder());
        
        JPanel left = new JPanel(new BorderLayout());
        left.add(runState, BorderLayout.WEST);
        left.add(phase, BorderLayout.CENTER);
        
        add(left, BorderLayout.WEST);
        add(settings, BorderLayout.CENTER);
    }
    
    public void refresh()
    {
        final ByRef<String> runStateString = ByRef.create(null);
        final ByRef<String> phaseString = ByRef.create(null);
        final ByRef<String> settingsString = ByRef.create(null);
        final Agent a = debugger.getAgentProxy().getAgent();
        
        debugger.getAgentProxy().execute(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                runStateString.value = debugger.getAgentProxy().isRunning() ? "Running" : "Idle";
                phaseString.value = a.decisionCycle.current_phase.toString().toLowerCase();
                settingsString.value = getSettings(a);
                return null;
            }});
        runState.setText("<html><b>" + runStateString.value + "</b></html>");
        phase.setText("<html><b>" + phaseString.value + "</b></html>");
        settings.setText(settingsString.value);
    }
    
    private String getSettings(Agent a)
    {
        StringBuilder b = new StringBuilder("<html>");
        b.append(status("warnings", a.getPrinter().isPrintWarnings()) + ", ");
        b.append(status("waitsnc", a.decider.isWaitsnc()) + ", ");
        b.append(status("learn", a.chunker.isLearningOn()) + ", ");
        b.append(status("save-backtraces", a.explain.isEnabled()));
        b.append("</html>");
        
        return b.toString();
    }
    
    private String status(String name, boolean value)
    {
        return "<b>" + name + ":</b>&nbsp;" + (value ? "on" : "off");
    }
}