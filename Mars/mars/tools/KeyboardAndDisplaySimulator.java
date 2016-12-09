   package mars.tools;
   import mars.util.Binary;
   import mars.venus.*;
   import javax.swing.*;
   import javax.swing.border.*;
   import javax.swing.event.*;
   import java.awt.*;
   import java.awt.event.*;
   import java.util.*;
   import mars.Globals;
   import mars.venus.RunSpeedPanel;
   import mars.mips.hardware.*;
   import mars.simulator.Exceptions;
   import javax.swing.text.DefaultCaret;


/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject
to the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

   /**
	 * Keyboard and Display Simulator.  It can be run either as a stand-alone Java application having
	 * access to the mars package, or through MARS as an item in its Tools menu.  It makes
	 * maximum use of methods inherited from its abstract superclass AbstractMarsToolAndApplication.
	 * Pete Sanderson<br>
	 * Version 1.0, 24 July 2008.<br>
	 * Version 1.1, 24 November 2008 corrects two omissions: (1) the tool failed to register as an observer
	 *    of kernel text memory when counting instruction executions for transmitter ready bit
	 *    reset delay, and (2) the tool failed to test the Status register's Exception Level bit before
	 *    raising the exception that results in the interrupt (if the Exception Level bit is 1, that
	 *    means an interrupt is being processed, so disable further interrupts).
	 *
	 * Version 1.2, 6 August 2009, soft-codes the MMIO register locations for new memory configuration
	 *    feature of MARS 3.7.  Previously memory segment addresses were fixed and final.  Now they
	 *    can be modified dynamically so the tool has to get its values dynamically as well.
	 *
	 */
    public class KeyboardAndDisplaySimulator extends AbstractMarsToolAndApplication {

      private static String version = "Version 1.3";
      private static String heading =  "Keyboard and Display MMIO Simulator";
      public static Dimension preferredTextAreaDimension = new Dimension(400,200);
      private static Insets textAreaInsets = new Insets(4,4,4,4);

      // Time delay to process Transmitter Data is simulated by counting instruction executions.
   	// After this many executions, the Transmitter Controller Ready bit set to 1.
      private final TransmitterDelayTechnique[] delayTechniques = {
                                new FixedLengthDelay(),
         							  new UniformlyDistributedDelay(),
         							  new NormallyDistributedDelay()
         };
      public static int RECEIVER_CONTROL;    // keyboard Ready in low-order bit
      public static int RECEIVER_DATA;       // keyboard character in low-order byte
      public static int TRANSMITTER_CONTROL; // display Ready in low-order bit
      public static int TRANSMITTER_DATA;    // display character in low-order byte
   	// These are used to track instruction counts to simulate driver delay of Transmitter Data
      private boolean countingInstructions;
      private int instructionCount;
      private int transmitDelayInstructionCountLimit;
      private int currentDelayInstructionLimit;

   	// Should the transmitted character be displayed before the transmitter delay period?
   	// If not, hold onto it and print at the end of delay period.
      private char characterToDisplay;
      private boolean displayAfterDelay = true;

   	// Major GUI components
      private JPanel keyboardAndDisplay;
      private JScrollPane displayScrollPane;
      private JTextArea display;
      private JPanel displayPanel, displayOptions;
      private JComboBox delayTechniqueChooser;
      private DelayLengthPanel delayLengthPanel;
      private JSlider delayLengthSlider;
      private JCheckBox displayAfterDelayCheckBox;
      private JPanel keyboardPanel;
      private JScrollPane keyAccepterScrollPane;
      private JTextArea keyEventAccepter;
      private JButton fontButton;
      private Font defaultFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
   	/**
   	 * Simple constructor, likely used to run a stand-alone keyboard/display simulator.
   	 * @param title String containing title for title bar
   	 * @param heading String containing text for heading shown in upper part of window.
   	 */
       public KeyboardAndDisplaySimulator(String title, String heading) {
         super(title,heading);
      }

   	 /**
   	  *  Simple constructor, likely used by the MARS Tools menu mechanism
   	  */
       public KeyboardAndDisplaySimulator() {
         super("Keyboard and Display MMIO Simulator, " + version, heading);
      }


   	/**
   	 * Main provided for pure stand-alone use.  Recommended stand-alone use is to write a
   	 * driver program that instantiates a KeyboardAndDisplaySimulator object then invokes its go() method.
   	 * "stand-alone" means it is not invoked from the MARS Tools menu.  "Pure" means there
   	 * is no driver program to invoke the application.
   	 */
       public static void main(String[] args) {
         new KeyboardAndDisplaySimulator("Keyboard and Display MMIO Simulator stand-alone, "+version,heading).go();
      }


       /**
   	  *  Required MarsTool method to return Tool name.
   	  *  @return  Tool name.  MARS will display this in menu item.
   	  */
       public String getName() {
         return "Keyboard and Display Simulator";
      }

   	// Set the MMIO addresses.  Prior to MARS 3.7 these were final because
   	// MIPS address space was final as well.  Now we will get MMIO base address
   	// each time to reflect possible change in memory configuration. DPS 6-Aug-09
       protected void initializePreGUI() {
         RECEIVER_CONTROL    = Memory.memoryMapBaseAddress; //0xffff0000; // keyboard Ready in low-order bit
         RECEIVER_DATA       = Memory.memoryMapBaseAddress + 4; //0xffff0004; // keyboard character in low-order byte
         TRANSMITTER_CONTROL = Memory.memoryMapBaseAddress + 8; //0xffff0008; // display Ready in low-order bit
         TRANSMITTER_DATA    = Memory.memoryMapBaseAddress + 12; //0xffff000c; // display character in low-order byte
      }


      /**
   	 *  Override the inherited method, which registers us as an Observer over the static data segment
   	 *  (starting address 0x10010000) only.
   	 *
   	 *  When user enters keystroke, set RECEIVER_CONTROL and RECEIVER_DATA using the action listener.
   	 *  When user loads word (lw) from RECEIVER_DATA (we are notified of the read), then clear RECEIVER_CONTROL.
   	 *  When user stores word (sw) to TRANSMITTER_DATA (we are notified of the write), then clear TRANSMITTER_CONTROL, read TRANSMITTER_DATA,
   	 *  echo the character to display, wait for delay period, then set TRANSMITTER_CONTROL.
   	 *
   	 *  If you use the inherited GUI buttons, this method is invoked when you click "Connect" button on MarsTool or the
   	 *  "Assemble and Run" button on a Mars-based app.
   	 */
       protected void addAsObserver() {
       	// Set transmitter Control ready bit to 1, means we're ready to accept display character.
         updateMMIOControl(TRANSMITTER_CONTROL, readyBitSet(TRANSMITTER_CONTROL));
          // We want to be an observer only of MIPS reads from RECEIVER_DATA and writes to TRANSMITTER_DATA.
          // Use the Globals.memory.addObserver() methods instead of inherited method to achieve this.
         addAsObserver(RECEIVER_DATA,RECEIVER_DATA);
         addAsObserver(TRANSMITTER_DATA, TRANSMITTER_DATA);
      	// We want to be notified of each instruction execution, because instruction count is the
      	// basis for delay in re-setting (literally) the TRANSMITTER_CONTROL register.  SPIM does
      	// this too.  This simulates the time required for the display unit to process the
      	// TRANSMITTER_DATA.
         addAsObserver(Memory.textBaseAddress, Memory.textLimitAddress);
         addAsObserver(Memory.kernelTextBaseAddress, Memory.kernelTextLimitAddress);
      }


   	/**
   	 *  Method that constructs the main display area.  It is organized vertically
   	 *  into two major components: the display and the keyboard.  The display itself
   	 *  is a JTextArea and it echoes characters placed into the low order byte of
   	 *  the Transmitter Data location, 0xffff000c.  They keyboard is also a JTextArea
   	 *  places each typed character into the Receive Data location 0xffff0004.
   	 *  @return the GUI component containing these two areas
   	 */
       protected JComponent buildMainDisplayArea() {
         keyboardAndDisplay = new JPanel(new GridLayout(2,1));
         keyboardAndDisplay.add(buildDisplay());
         keyboardAndDisplay.add(buildKeyboard());
         return keyboardAndDisplay;
      }


      //////////////////////////////////////////////////////////////////////////////////////
      //  Rest of the protected methods.  These all override do-nothing methods inherited from
   	//  the abstract superclass.
      //////////////////////////////////////////////////////////////////////////////////////

      /**
   	 * Update display when connected MIPS program accesses (data) memory.
   	 * @param memory the attached memory
   	 * @param accessNotice information provided by memory in MemoryAccessNotice object
   	 */
       protected void processMIPSUpdate(Observable memory, AccessNotice accessNotice) {
         MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;
      	// If MIPS program has just read (loaded) the receiver (keyboard) data register,
      	// then clear the Ready bit to indicate there is no longer a keystroke available.
      	// If Ready bit was initially clear, they'll get the old keystroke -- serves 'em right
      	// for not checking!
         if (notice.getAddress()==RECEIVER_DATA && notice.getAccessType()==AccessNotice.READ) {
            updateMMIOControl(RECEIVER_CONTROL, readyBitCleared(RECEIVER_CONTROL));
         }
      	// MIPS program has just written (stored) the transmitter (display) data register.  If transmitter
      	// Ready bit is clear, device is not ready yet so ignore this event -- serves 'em right for not checking!
      	// If transmitter Ready bit is set, then clear it to indicate the display device is processing the character.
      	// Also start an intruction counter that will simulate the delay of the slower
      	// display device processing the character.
         if (isReadyBitSet(TRANSMITTER_CONTROL) && notice.getAddress()==TRANSMITTER_DATA && notice.getAccessType()==AccessNotice.WRITE) {
            updateMMIOControl(TRANSMITTER_CONTROL, readyBitCleared(TRANSMITTER_CONTROL));
            characterToDisplay = (char) (notice.getValue() & 0x000000FF);
            if (!displayAfterDelay) display.append(""+characterToDisplay);
            this.countingInstructions = true;
            this.instructionCount = 0;
            this.transmitDelayInstructionCountLimit = generateDelay();
         }
      	// We have been notified of a MIPS instruction execution.
      	// If we are in transmit delay period, increment instruction count and if limit
      	// has been reached, set the transmitter Ready flag to indicate the MIPS program
      	// can write another character to the transmitter data register.  If the Interrupt-Enabled
      	// bit had been set by the MIPS program, generate an interrupt!
         if (   this.countingInstructions &&
                notice.getAccessType()==AccessNotice.READ &&
                (Memory.inTextSegment(notice.getAddress()) || Memory.inKernelTextSegment(notice.getAddress()))) {
            this.instructionCount++;
            if (this.instructionCount >= this.transmitDelayInstructionCountLimit) {
               if (displayAfterDelay) display.append(""+characterToDisplay);
               this.countingInstructions = false;
               int updatedTransmitterControl =  readyBitSet(TRANSMITTER_CONTROL);
               updateMMIOControl(TRANSMITTER_CONTROL, updatedTransmitterControl);
               if (updatedTransmitterControl != 1
                   && (Coprocessor0.getValue(Coprocessor0.STATUS) & 2)==0  // Added by Carl Hauser Nov 2008
                   && (Coprocessor0.getValue(Coprocessor0.STATUS) & 1)==1) {
               // interrupt-enabled bit is set in both Tranmitter Control and in
               // Coprocessor0 Status register, and Interrupt Level Bit is 0, so trigger external interrupt.
                  mars.simulator.Simulator.externalInterruptingDevice = Exceptions.EXTERNAL_INTERRUPT_DISPLAY;
               }
            }
         }
      }


   	/**
   	 *  Initialization code to be executed after the GUI is configured.  Overrides inherited default.
   	 */

       protected void initializePostGUI() {
         initializeTransmitDelaySimulator();
         keyEventAccepter.requestFocusInWindow();
      }


   	/**
   	 *  Method to reset counters and display when the Reset button selected.
   	 *  Overrides inherited method that does nothing.
   	 */
       protected void reset() {
         initializeTransmitDelaySimulator();
         display.setText("");
         keyEventAccepter.setText("");
         keyEventAccepter.requestFocusInWindow();
         updateMMIOControl(TRANSMITTER_CONTROL, readyBitSet(TRANSMITTER_CONTROL));
      }

   	 /**
   	  *  Overrides default method, to provide a Help button for this tool/app.
   	  */

       protected JComponent getHelpComponent() {
         final String helpContent =
                              "Use this program to simulate Memory-Mapped I/O (MMIO) for a keyboard input device and character "+
                              "display output device.  It may be run either from MARS' Tools menu or as a stand-alone application. "+
            						"For the latter, simply write a driver to instantiate a mars.tools.KeyboardAndDisplaySimulator object "+
            						"and invoke its go() method.\n"+
            						"\n"+
            						"While the tool is connected to MIPS, each keystroke in the text area causes the corresponding ASCII "+
            						"code to be placed in the Receiver Data register (low-order byte of memory word "+Binary.intToHexString(RECEIVER_DATA)+"), and the "+
            						"Ready bit to be set to 1 in the Receiver Control register (low-order bit of "+Binary.intToHexString(RECEIVER_CONTROL)+").  The Ready "+
            						"bit is automatically reset to 0 when the MIPS program reads the Receiver Data using an 'lw' instruction.\n"+
            						"\n"+
            						"A program may write to the display area by detecting the Ready bit set (1) in the Transmitter Control "+
            						"register (low-order bit of memory word "+Binary.intToHexString(TRANSMITTER_CONTROL)+"), then storing the ASCII code of the character to be "+
            						"displayed in the Transmitter Data register (low-order byte of "+Binary.intToHexString(TRANSMITTER_DATA)+") using a 'sw' instruction.  This "+
            						"triggers the simulated display to clear the Ready bit to 0, delay awhile to simulate processing the data, "+
            						"then set the Ready bit back to 1.  The delay is based on a count of executed MIPS instructions.\n"+
            						"\n"+
            						"In a polled approach to I/O, a MIPS program idles in a loop, testing the device's Ready bit on each "+
            						"iteration until it is set to 1 before proceeding.  This tool also supports an interrupt-driven approach "+
            						"which requires the program to provide an interrupt handler but allows it to perform useful processing "+
            						"instead of idly looping.  When the device is ready, it signals an interrupt and the MARS simuator will "+
            						"transfer control to the interrupt handler.  Note: in MARS, the interrupt handler has to co-exist with the "+
            						"exception handler in kernel memory, both having the same entry address.  Interrupt-driven I/O is enabled "+
            						"when the MIPS program sets the Interrupt-Enable bit in the device's control register.  Details below.\n"+
            						"\n"+
            						"Upon setting the Receiver Controller's Ready bit to 1, its Interrupt-Enable bit (bit position 1) is tested. "+
            						"If 1, then an External Interrupt will be generated.  Before executing the next MIPS instruction, the runtime "+
            						"simulator will detect the interrupt, place the interrupt code (0) into bits 2-6 of Coprocessor 0's Cause "+
            						"register ($13), set bit 8 to 1 to identify the source as keyboard, place the program counter value (address "+
            						"of the NEXT instruction to be executed) into its EPC register ($14), and check to see if an interrupt/trap "+
            						"handler is present (looks for instruction code at address 0x80000180).  If so, the program counter is set to "+
            						"that address.  If not, program execution is terminated with a message to the Run I/O tab.  The Interrupt-Enable "+
            						"bit is 0 by default and has to be set by the MIPS program if interrupt-driven input is desired.  Interrupt-driven "+
            						"input permits the program to perform useful tasks instead of idling in a loop polling the Receiver Ready bit!  "+
            						"Very event-oriented.  The Ready bit is supposed to be read-only but in MARS it is not.\n"+
            						"\n"+
            						"A similar test and potential response occurs when the Transmitter Controller's Ready bit is set to 1.  This "+
            						"occurs after the simulated delay described above.  The only difference is the Cause register bit to identify "+
            						"the (simulated) display as external interrupt source is bit position 9 rather than 8.  This permits you to "+
            						"write programs that perform interrupt-driven output - the program can perform useful tasks while the "+
            						"output device is processing its data.  Much better than idling in a loop polling the Transmitter Ready bit! "+
            						"The Ready bit is supposed to be read-only but in MARS it is not.\n"+
            						"\n"+
            						"IMPORTANT NOTE: The Transmitter Controller Ready bit is set to its initial value of 1 only when you click the tool's "+
            						"'Connect to MIPS' button ('Assemble and Run' in the stand-alone version) or the tool's Reset button!  If you run a "+
            						"MIPS program and reset it in MARS, the controller's Ready bit is cleared to 0!  Configure the Data Segment Window to "+
            						"display the MMIO address range so you can directly observe values stored in the MMIO addresses given above.\n"+
            						"\n"+
            						"Contact Pete Sanderson at psanderson@otterbein.edu with questions or comments.\n";
         JButton help = new JButton("Help");
         help.addActionListener(
                new ActionListener() {
                   public void actionPerformed(ActionEvent e) {
                     JTextArea ja = new JTextArea(helpContent);
                     ja.setRows(30);
                     ja.setColumns(60);
                     ja.setLineWrap(true);
                     ja.setWrapStyleWord(true);
                     JOptionPane.showMessageDialog(theWindow, new JScrollPane(ja),
                         "Simulating the Keyboard and Display", JOptionPane.INFORMATION_MESSAGE);
                  }
               });
         return help;
      }



      //////////////////////////////////////////////////////////////////////////////////////
      //  Private methods defined to support the above.
      //////////////////////////////////////////////////////////////////////////////////////

   	////////////////////////////////////////////////////////////////////////////////////////
   	// UI components and layout for upper part of GUI, where simulated display is located.
       private JComponent buildDisplay() {
         displayPanel = new JPanel(new BorderLayout());
         TitledBorder tb = new TitledBorder("DISPLAY: Characters stored to Transmitter Data Register ("+Binary.intToHexString(TRANSMITTER_DATA)+") are echoed here");
         tb.setTitleJustification(TitledBorder.CENTER);
         displayPanel.setBorder(tb);
         display = new JTextArea();
         display.setFont(defaultFont);
         display.setEditable(false);
         display.setMargin(textAreaInsets);

	// 2011-07-29: Patrik Lundin, patrik@lundin.info
	// Added code so display autoscrolls. 
		DefaultCaret caret = (DefaultCaret)display.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
	// end added autoscrolling

         displayScrollPane = new JScrollPane(display);
         displayScrollPane.setPreferredSize(preferredTextAreaDimension);
         displayPanel.add(displayScrollPane);

         displayOptions = new JPanel();
         delayTechniqueChooser = new JComboBox(delayTechniques);
         delayTechniqueChooser.setToolTipText("Technique for determining simulated transmitter device processing delay");
         delayTechniqueChooser.addActionListener(
                new ActionListener() {
                   public void actionPerformed(ActionEvent e) {
                     transmitDelayInstructionCountLimit = generateDelay();
                  }
               });
         delayLengthPanel = new DelayLengthPanel();
         displayAfterDelayCheckBox = new JCheckBox("DAD", true);
         displayAfterDelayCheckBox.setToolTipText("Display After Delay: if checked, transmitter data not displayed until after delay");
         displayAfterDelayCheckBox.addActionListener(
                new ActionListener() {
                   public void actionPerformed(ActionEvent e) {
                     displayAfterDelay = displayAfterDelayCheckBox.isSelected();
                  }
               });
         
         //font button to display font
         fontButton = new JButton("Font");
         fontButton.setToolTipText("Select the font for the display panel");
         fontButton.addActionListener(new FontChanger()) ;
         displayOptions.add(fontButton);
         displayOptions.add(displayAfterDelayCheckBox);
         displayOptions.add(delayTechniqueChooser);
         displayOptions.add(delayLengthPanel);
         displayPanel.add(displayOptions,BorderLayout.SOUTH);
         return displayPanel;
      }



   	//////////////////////////////////////////////////////////////////////////////////////
   	// UI components and layout for lower part of GUI, where simulated keyboard is located.
       private JComponent buildKeyboard() {
         keyboardPanel = new JPanel(new BorderLayout());
         keyEventAccepter = new JTextArea();
         keyEventAccepter.setEditable(true);
         keyEventAccepter.setFont(defaultFont);
         keyEventAccepter.setMargin(textAreaInsets);
         keyAccepterScrollPane = new JScrollPane(keyEventAccepter);
         keyAccepterScrollPane.setPreferredSize(preferredTextAreaDimension);
         keyEventAccepter.addKeyListener(new KeyboardKeyListener());
         keyboardPanel.add(keyAccepterScrollPane);
         TitledBorder tb =new TitledBorder("KEYBOARD: Characters typed in this text area are written to Receiver Data Register ("+Binary.intToHexString(RECEIVER_DATA)+")");
         tb.setTitleJustification(TitledBorder.CENTER);
         keyboardPanel.setBorder(tb);
         return keyboardPanel;
      }

   	 ////////////////////////////////////////////////////////////////////
       // update the MMIO Control register memory cell. We will delegate.
       private void updateMMIOControl(int addr, int intValue) {
         updateMMIOControlAndData(addr, intValue, 0, 0, true);
      }


   	 /////////////////////////////////////////////////////////////////////
       // update the MMIO Control and Data register pair -- 2 memory cells. We will delegate.
       private void updateMMIOControlAndData(int controlAddr, int controlValue, int dataAddr, int dataValue) {
         updateMMIOControlAndData(controlAddr, controlValue, dataAddr, dataValue, false);
      }


   	 /////////////////////////////////////////////////////////////////////////////////////////////////////
       // This one does the work: update the MMIO Control and optionally the Data register as well
   	 // NOTE: last argument TRUE means update only the MMIO Control register; FALSE means update both Control and Data.
       private synchronized void updateMMIOControlAndData(int controlAddr, int controlValue, int dataAddr, int dataValue, boolean controlOnly) {
         if (!this.isBeingUsedAsAMarsTool || (this.isBeingUsedAsAMarsTool && connectButton.isConnected())) {
            synchronized (Globals.memoryAndRegistersLock) {
               try {
                  Globals.memory.setRawWord(controlAddr, controlValue);
                  if (!controlOnly) Globals.memory.setRawWord(dataAddr, dataValue);
               }
                   catch (AddressErrorException aee) {
                     System.out.println("Tool author specified incorrect MMIO address!"+aee);
                     System.exit(0);
                  }
            }
         	// HERE'S A HACK!!  Want to immediately display the updated memory value in MARS
         	// but that code was not written for event-driven update (e.g. Observer) --
         	// it was written to poll the memory cells for their values.  So we force it to do so.

            if (Globals.getGui() != null && Globals.getGui().getMainPane().getExecutePane().getTextSegmentWindow().getCodeHighlighting() ) {
               Globals.getGui().getMainPane().getExecutePane().getDataSegmentWindow().updateValues();
            }
         }
      }



     /////////////////////////////////////////////////////////////////////
     // Return value of the given MMIO control register after ready (low order) bit set (to 1).
     // Have to preserve the value of Interrupt Enable bit (bit 1)
       private static boolean isReadyBitSet(int mmioControlRegister) {
         try {
            return (Globals.memory.get(mmioControlRegister, Memory.WORD_LENGTH_BYTES) & 1) == 1;
         }
             catch (AddressErrorException aee) {
               System.out.println("Tool author specified incorrect MMIO address!"+aee);
               System.exit(0);
            }
         return false; // to satisfy the compiler -- this will never happen.
      }


     /////////////////////////////////////////////////////////////////////
     // Return value of the given MMIO control register after ready (low order) bit set (to 1).
     // Have to preserve the value of Interrupt Enable bit (bit 1)
       private static int readyBitSet(int mmioControlRegister) {
         try {
            return Globals.memory.get(mmioControlRegister, Memory.WORD_LENGTH_BYTES) | 1;
         }
             catch (AddressErrorException aee) {
               System.out.println("Tool author specified incorrect MMIO address!"+aee);
               System.exit(0);
            }
         return 1; // to satisfy the compiler -- this will never happen.
      }

     /////////////////////////////////////////////////////////////////////
     //  Return value of the given MMIO control register after ready (low order) bit cleared (to 0).
     // Have to preserve the value of Interrupt Enable bit (bit 1). Bits 2 and higher don't matter.
       private static int readyBitCleared(int mmioControlRegister) {
         try {
            return Globals.memory.get(mmioControlRegister, Memory.WORD_LENGTH_BYTES) & 2;
         }
             catch (AddressErrorException aee) {
               System.out.println("Tool author specified incorrect MMIO address!"+aee);
               System.exit(0);
            }
         return 0; // to satisfy the compiler -- this will never happen.
       }


   	/////////////////////////////////////////////////////////////////////
   	// Transmit delay is simulated by counting instruction executions.
   	// Here we simly initialize (or reset) the variables.
       private void initializeTransmitDelaySimulator() {
         this.countingInstructions = false;
         this.instructionCount = 0;
         this.transmitDelayInstructionCountLimit = this.generateDelay();
      }


   	/////////////////////////////////////////////////////////////////////
   	//  Calculate transmitter delay (# instruction executions) based on
   	//  current combo box and slider settings.

       private int generateDelay() {
         double sliderValue = delayLengthPanel.getDelayLength();
         TransmitterDelayTechnique technique = (TransmitterDelayTechnique) delayTechniqueChooser.getSelectedItem();
         return technique.generateDelay(sliderValue);
      }



   	///////////////////////////////////////////////////////////////////////////////////
   	//
   	//  Class to grab keystrokes going to keyboard echo area and send them to MMIO area
   	//

       private class KeyboardKeyListener implements KeyListener {
          public void keyTyped(KeyEvent e) {
            int updatedReceiverControl = readyBitSet(RECEIVER_CONTROL);
            updateMMIOControlAndData(RECEIVER_CONTROL, updatedReceiverControl, RECEIVER_DATA,  e.getKeyChar() & 0x00000ff);
            if (updatedReceiverControl != 1
                && (Coprocessor0.getValue(Coprocessor0.STATUS) & 2)==0   // Added by Carl Hauser Nov 2008
            	 && (Coprocessor0.getValue(Coprocessor0.STATUS) & 1)==1) {
               // interrupt-enabled bit is set in both Receiver Control and in
            	// Coprocessor0 Status register, and Interrupt Level Bit is 0, so trigger external interrupt.
               mars.simulator.Simulator.externalInterruptingDevice = Exceptions.EXTERNAL_INTERRUPT_KEYBOARD;
            }
        }


        /* Ignore key pressed event from the text field. */
        public void keyPressed(KeyEvent e) {
        }

        /* Ignore key released event from the text field. */
        public void keyReleased(KeyEvent e) {
            }
        }


   	//////////////////////////////////////////////////////////////////////////////////
   	//
   	//  Class for selecting transmitter delay lengths (# of MIPS instruction executions).
   	//

        private class DelayLengthPanel extends JPanel {
         private final static int DELAY_INDEX_MIN = 0;
         private final static int DELAY_INDEX_MAX = 40;
         private final static int DELAY_INDEX_INIT = 4;
         private double[] delayTable = {
               1,    2,    3,    4,    5,   10,   20,   30,   40,   50,  100,  // 0-10
            	    150,  200,  300,  400,  500,  600,  700,  800,  900, 1000,  //11-20
                  1500, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000,10000,  //21-30
                 20000,40000,60000,80000,100000,200000,400000,600000,800000,1000000//31-40
            	};
         private JLabel sliderLabel=null;
         private volatile int delayLengthIndex = DELAY_INDEX_INIT;

          public DelayLengthPanel() {
            super(new BorderLayout());
            delayLengthSlider = new JSlider(JSlider.HORIZONTAL, DELAY_INDEX_MIN,DELAY_INDEX_MAX,DELAY_INDEX_INIT);
            delayLengthSlider.setSize(new Dimension(100,(int)delayLengthSlider.getSize().getHeight()));
            delayLengthSlider.setMaximumSize(delayLengthSlider.getSize());
            delayLengthSlider.addChangeListener(new DelayLengthListener());
            sliderLabel = new JLabel(setLabel(delayLengthIndex));
            sliderLabel.setHorizontalAlignment(JLabel.CENTER);
            sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            this.add(sliderLabel, BorderLayout.NORTH);
            this.add(delayLengthSlider, BorderLayout.CENTER);
            this.setToolTipText("Parameter for simulated delay length (MIPS instruction execution count)");
         }

       // returns current delay length setting, in instructions.
          public double getDelayLength() {
            return delayTable[delayLengthIndex];
         }


       // set label wording depending on current speed setting
          private String setLabel(int index) {
            return "Delay length: "+((int)delayTable[index])+" instruction executions";
         }


       // Both revises label as user slides and updates current index when sliding stops.
          private class DelayLengthListener implements ChangeListener {
             public void stateChanged(ChangeEvent e) {
               JSlider source = (JSlider)e.getSource();
               if (!source.getValueIsAdjusting()) {
                  delayLengthIndex = (int)source.getValue();
                  transmitDelayInstructionCountLimit = generateDelay();
               }
               else {
                  sliderLabel.setText(setLabel(source.getValue()));
               }
            }
         }
      }

   	///////////////////////////////////////////////////////////////////
   	//
   	//Interface and classes for Transmitter Delay-generating techniques.
        //

        private interface TransmitterDelayTechnique {
          public int generateDelay(double parameter);
      }

   	 // Delay value is fixed, and equal to slider value.
        private class FixedLengthDelay implements TransmitterDelayTechnique {
          public String toString() {
            return "Fixed transmitter delay, select using slider";
         }
          public int generateDelay(double fixedDelay) {
            return (int) fixedDelay;
         }
      }

   	 // Randomly pick value from range 1 to slider setting, uniform distribution
   	 // (each value has equal probability of being chosen).
        private class UniformlyDistributedDelay implements TransmitterDelayTechnique {
         Random randu;
          public UniformlyDistributedDelay() {
            randu = new Random();
         }
          public String toString() {
            return "Uniformly distributed delay, min=1, max=slider";
         }
          public int generateDelay(double max) {
            return randu.nextInt((int)max)+1;
         }
      }

   	// Pretty badly-hacked normal distribution, but is more realistic than uniform!
   	// Get sample from Normal(0,1) -- mean=0, s.d.=1 -- multiply it by slider
   	// value, take absolute value to make sure we don't get negative,
   	// add 1 to make sure we don't get 0.
        private class NormallyDistributedDelay implements TransmitterDelayTechnique {
            Random randn;
            public NormallyDistributedDelay() {
                randn = new Random();
            }
            public String toString() {
                return "'Normally' distributed delay: floor(abs(N(0,1)*slider)+1)";
            }
            public int generateDelay(double mult) {
                return (int) (Math.abs(randn.nextGaussian()*mult)+1);
            }
        }

    /**
     *  Font dialog for the display panel
     *  Almost all of the code is used from the SettingsHighlightingAction
     *  class.
    */
        
    private class FontSettingDialog extends AbstractFontSettingDialog {
         private boolean resultOK;
      
          public FontSettingDialog(Frame owner, String title, Font currentFont) {
            super(owner, title, true, currentFont);
         }
         
          private Font showDialog() {
            resultOK = true;
           // Because dialog is modal, this blocks until user terminates the dialog.
            this.setVisible(true);
            return resultOK ? getFont() : null;
         }
      	
          protected void closeDialog() {
            this.setVisible(false);
         }

         private void performCancel() {
            resultOK = false;
         }
      	
      	// Control buttons for the dialog.  
          protected Component buildControlPanel() {
            Box controlPanel = Box.createHorizontalBox();
            JButton okButton = new JButton("OK");
            okButton.addActionListener(
                   new ActionListener() {
                      public void actionPerformed(ActionEvent e) {
                        apply(getFont());
                        closeDialog();
                     }
                  });
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(
                   new ActionListener() {
                      public void actionPerformed(ActionEvent e) {
                        performCancel(); 
                        closeDialog();
                     }
                  });	
            JButton resetButton = new JButton("Reset");
            resetButton.addActionListener(
                   new ActionListener() {
                      public void actionPerformed(ActionEvent e) {
                        reset();
                     }
                  });
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(okButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(cancelButton);
            controlPanel.add(Box.createHorizontalGlue());		 
            controlPanel.add(resetButton);
            controlPanel.add(Box.createHorizontalGlue());
            return controlPanel;
         }
      
        // Change the font for the keyboard and display
        protected void apply(Font font) {
            display.setFont(font);
            keyEventAccepter.setFont(font);
        }
      
     }
        
    private class FontChanger implements ActionListener {
          public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            FontSettingDialog fontDialog = new FontSettingDialog(null, "Select Text Font", display.getFont());
            Font newFont = fontDialog.showDialog();
          }
    }

}