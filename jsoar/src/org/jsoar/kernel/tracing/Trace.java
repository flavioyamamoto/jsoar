/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 16, 2008
 */
package org.jsoar.kernel.tracing;

import java.io.OutputStreamWriter;
import java.util.EnumSet;
import java.util.Formattable;
import java.util.Formatter;

/**
 * This class encapsulates the tracing mechanism used throughout the kernel
 * into a single location.
 * 
 * @author ray
 */
public class Trace
{
    /**
     * gsysparam.h:92:TRACE_*_SYSPARAM
     * 
     * @author ray
     */
    public static enum Category
    {
        /** gsysparam.h:92:TRACE_VERBOSE */
        VERBOSE,
        
        /** gsysparam.h:92:TRACE_CONTEXT_DECISIONS_SYSPARAM */
        CONTEXT_DECISIONS(true),
        
        /** gsysparam.h:93:TRACE_PHASES_SYSPARAM */
        PHASES,

        
        /** gsysparam.h:97:TRACE_FIRINGS_OF_USER_PRODS_SYSPARAM */
        FIRINGS_OF_USER_PRODS,
        
        /** gsysparam.h:98:TRACE_FIRINGS_OF_DEFAULT_PRODS_SYSPARAM */
        FIRINGS_OF_DEFAULT_PRODS,
        
        /** gsysparam.h:99:TRACE_FIRINGS_OF_CHUNKS_SYSPARAM */
        FIRINGS_OF_CHUNKS,
        
        /** gsysparam.h:100:TRACE_FIRINGS_OF_JUSTIFICATIONS_SYSPARAM */
        FIRINGS_OF_JUSTIFICATIONS,
        
        /** gsysparam.h:101:TRACE_FIRINGS_OF_TEMPLATES_SYSPARAM */
        FIRINGS_OF_TEMPLATES,

        
        /** gsysparam.h:103:TRACE_FIRINGS_PREFERENCES_SYSPARAM */
        FIRINGS_PREFERENCES,
        
        /** gsysparam.h:104:TRACE_WM_CHANGES_SYSPARAM */
        WM_CHANGES,
        
        /** gsysparam.h:105:TRACE_CHUNK_NAMES_SYSPARAM */
        CHUNK_NAMES,
        
        /** gsysparam.h:106:TRACE_JUSTIFICATION_NAMES_SYSPARAM */
        JUSTIFICATION_NAMES,
        
        /** gsysparam.h:107:TRACE_CHUNKS_SYSPARAM */
        CHUNKS,
        
        /** gsysparam.h:108:TRACE_JUSTIFICATIONS_SYSPARAM */
        JUSTIFICATIONS,
        
        /** gsysparam.h:109:TRACE_BACKTRACING_SYSPARAM */
        BACKTRACING,
        
        /** gsysparam.h:112:TRACE_LOADING_SYSPARAM */
        LOADING(true),
        
        /** gsysparam.h:146:TRACE_OPERAND2_REMOVALS_SYSPARAM */
        OPERAND2_REMOVALS ,
        
        /** gsysparam.h:159:TRACE_INDIFFERENT_SYSPARAM */
        INDIFFERENT,
        
        /** gsysparam.h:176:TRACE_RL_SYSPARAM */
        RL,
        
        /** New in jsoar for debugging waterfall */
        WATERFALL;
        
        public final boolean defaultSetting;
        
        Category()
        {
            this.defaultSetting = false;
        }
        
        Category(boolean defaultSetting)
        {
            this.defaultSetting = defaultSetting;
        }
    }
    
    /**
     * How much information to print about the wmes matching an instantiation. 
     * These values can typically be or'd together in an EnumSet.
     * 
     * <p>gsysparam.h:72:wme_trace_type
     * 
     * @author ray
     */
    public static enum WmeTraceType
    {
        /**
         * don't print anything
         * 
         * <p>gsysparam.h:67:NONE_WME_TRACE
         */
        NONE,
        
        /**
         * print just timetag
         * 
         * <p>gsysparam.h:68:TIMETAG_WME_TRACE
         */
        TIMETAG,
        
        /**
         * print whole wme
         * 
         * <p>gsysparam.h:69:FULL_WME_TRACE
         */
        FULL,
    }
    
    /**
     * <p>gsysparam.h:60:ms_trace_type
     * 
     * @author ray
     */
    public static enum MatchSetTraceType
    {
        MS_ASSERT, // print just assertions
        MS_RETRACT // print just retractions
    }
    
    /**
     * @return a new trace object that prints to stdout
     */
    public static Trace createStdOutTrace()
    {
        return new Trace(new Printer(new OutputStreamWriter(System.out), true));
    }
    
    private final Printer printer;
    private EnumSet<Category> settings = EnumSet.noneOf(Category.class);
    private boolean enabled = true;
    private WmeTraceType wmeTraceType = WmeTraceType.NONE;
    
    /**
     * @param printer
     */
    public Trace(Printer printer)
    {
        this.printer = printer;
        
        for(Category c : Category.values())
        {
            if(c.defaultSetting)
            {
                settings.add(c);
            }
        }
    }
    
    /**
     * @return The printer this trace is going to
     */
    public Printer getPrinter()
    {
        return this.printer;
    }

    
    /**
     * @return True if overall tracing is enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Control global tracing. If set to false, no tracing will be performed
     * 
     * @param enabled True to enable tracing, false to disable all tracing
     */
    public Trace setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }
    
    /**
     * Enable all trace categories
     * 
     * @return this 
     */
    public Trace enableAll()
    {
        setEnabled(true);
        settings = EnumSet.allOf(Category.class);
        this.setWmeTraceType(WmeTraceType.FULL);
        return this;
    }
    
    /**
     * Disable all trace categories
     * 
     * @return this
     */
    public Trace disableAll()
    {
        settings.clear();
        return this;
    }
    
    /**
     * @param c The category to query
     * @return True if tracing is enabled for the particular category
     */
    public boolean isEnabled(Category c)
    {
        return settings.contains(c);
    }
    
    public boolean isEnabled(EnumSet<Category> anyOf)
    {
        for(Category c : anyOf)
        {
            if(isEnabled(c))
            {
                return true;
            }
        }
        return false;
    }
    

    /**
     * Control category tracing. If set to false, no tracing will be performed
     * for the given category. Setting this to true will also change the value
     * of {@link #isEnabled()} to true.
     * 
     * @param c The category
     * @param enabled True to enable tracing for the given category, false to 
     *      disable tracing for the given category
     */
    public Trace setEnabled(Category c, boolean enabled)
    {
        if(enabled)
        {
            setEnabled(true);
            settings.add(c);
        }
        else
        {
            settings.remove(c);
        }
        return this;
    }

    /**
     * @return the wmeTraceType
     */
    public WmeTraceType getWmeTraceType()
    {
        return wmeTraceType;
    }

    /**
     * @param wmeTraceType the wmeTraceType to set
     */
    public void setWmeTraceType(WmeTraceType wmeTraceType)
    {
        this.wmeTraceType = wmeTraceType;
    }

    public Trace print(String format, Object... args)
    {
        if(enabled)
        {
            for(int i = 0; i < args.length; ++i)
            {
                // If it's traceable replace it with our proxy object.
                // TODO Restore args on return?
                if(args[i] instanceof Traceable)
                {
                    args[i] = new Proxy((Traceable) args[i]);
                }
            }
            printer.print(format, args);
        }
        return this;
        
    }
    
    public Trace print(String output)
    {
        if(enabled)
        {
            printer.print(output);
        }
        return this;
    }
    
    /**
     * Trace a string in a particular category.
     * 
     * @param c The category
     * @param format The printf-style format string
     * @param args printf arguments
     */
    public Trace print(Category c, String format, Object... args)
    {
        if(enabled && isEnabled(c))
        {
            print(format, args);
        }
        return this;
    }
    
    public Trace print(EnumSet<Category> anyOf, String format, Object... args)
    {
        if(enabled && isEnabled(anyOf))
        {
            print(format, args);
        }
        return this;
    }
    
    
    public Trace print(Category c, String output)
    {
        if(enabled && isEnabled(c))
        {
            print(output);
        }
        return this;
    }
    
    /**
     * Flush the underlying printer 
     */
    public void flush()
    {
        printer.flush();
    }
    
    public Trace startNewLine()
    {
        if(enabled)
        {
            printer.startNewLine();
        }
        return this;
    }
    
    /**
     * A wrapper object we put around all trace arguments that implement {@link Traceable}.
     * This gives us a way to give the traceable access to the Trace object in the
     * context of the Java formattable framework.
     * 
     * @author ray
     */
    private class Proxy implements Formattable
    {
        private final Traceable traceable;
        
        /**
         * @param traceable The traceable object
         */
        public Proxy(Traceable traceable)
        {
            this.traceable = traceable;
        }

        /* (non-Javadoc)
         * @see java.util.Formattable#formatTo(java.util.Formatter, int, int, int)
         */
        @Override
        public void formatTo(Formatter formatter, int flags, int width, int precision)
        {
            traceable.trace(Trace.this, formatter, flags, width, precision);
        }
        
    }
}
