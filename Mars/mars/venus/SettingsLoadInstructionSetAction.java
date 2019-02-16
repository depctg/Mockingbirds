package mars.venus;

import java.awt.event.ActionEvent;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import mars.Globals;
import mars.venus.GuiAction;
import mars.venus.VenusUI;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

public class SettingsLoadInstructionSetAction extends GuiAction {

   protected SettingsLoadInstructionSetAction(String name, Icon icon, String descrip, Integer mnemonic, KeyStroke accel, VenusUI gui) {
      super(name, icon, descrip, mnemonic, accel, gui);
   }

   public void actionPerformed(ActionEvent e) {
      JFileChooser fileChooser = new JFileChooser();
      if(fileChooser.showOpenDialog(this.mainUI) == 0) {
         String filepath = fileChooser.getSelectedFile().getPath();

         try {
            LuaValue expr = Globals.getLuaBinding().getGlobals().loadfile(filepath);
            expr.call();
            Globals.instructionSet.generateMatchMaps();
         } catch (LuaError err) {
            JOptionPane.showMessageDialog(this.mainUI, err.getMessage(), "Lua Error", 0);
         }
      }

   }
}
