package mars.lua;

import mars.ProcessingException;
import mars.ProgramStatement;
import mars.mips.hardware.*;
import mars.mips.instructions.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuaBinding extends TwoArgFunction {
    private Globals luaGlobals = JsePlatform.standardGlobals();

    public LuaBinding() {
        this.luaGlobals.load(this);
    }

    public Globals getGlobals() {
        return this.luaGlobals;
    }

    public LuaValue call(LuaValue modname, LuaValue env) {
        env.set("getgpr", new LuaBinding.LuaFunc_getgpr());
        env.set("setgpr", new LuaBinding.LuaFunc_setgpr());
        env.set("getpc", new LuaBinding.LuaFunc_getpc());
        env.set("loadb", new LuaBinding.LuaFunc_loadb());
        env.set("loadh", new LuaBinding.LuaFunc_loadh());
        env.set("loadw", new LuaBinding.LuaFunc_loadw());
        env.set("storeb", new LuaBinding.LuaFunc_storeb());
        env.set("storeh", new LuaBinding.LuaFunc_storeh());
        env.set("storew", new LuaBinding.LuaFunc_storew());
        env.set("branch", new LuaBinding.LuaFunc_branch());
        env.set("jump", new LuaBinding.LuaFunc_jump());
        env.set("register_instruction", new LuaBinding.LuaFunc_register_instruction());
        return LuaValue.NIL;
    }

    static class LuaFunc_branch extends OneArgFunction {
        public LuaValue call(LuaValue lvOffset) {
            int offset = lvOffset.checkint();
            InstructionSet.processBranch(offset);
            return LuaValue.NIL;
        }
    }

    static class LuaFunc_getgpr extends OneArgFunction {
        public LuaValue call(LuaValue lvNum) {
            int num = lvNum.checkint();
            int val = RegisterFile.getValue(num);
            return LuaValue.valueOf(val);
        }
    }

    static class LuaFunc_getpc extends ZeroArgFunction {
        public LuaValue call() {
            return LuaNumber.valueOf(RegisterFile.getProgramCounter());
        }
    }

    static class LuaFunc_jump extends OneArgFunction {
        public LuaValue call(LuaValue lvAddr) {
            int addr = lvAddr.checkint();
            InstructionSet.processJump(addr);
            return LuaValue.NIL;
        }
    }

    static class LuaFunc_loadb extends OneArgFunction {
        public LuaValue call(LuaValue lvAddr) {
            int addr = lvAddr.checkint();

            try {
                int val = mars.Globals.memory.getByte(addr);
                return LuaValue.valueOf(val);
            } catch (AddressErrorException var5) {
                System.err.printf("loadb(): address error: %#x%n", new Object[]{Integer.valueOf(addr)});
                return LuaValue.NIL;
            }
        }
    }

    static class LuaFunc_loadh extends OneArgFunction {
        public LuaValue call(LuaValue lvAddr) {
            int addr = lvAddr.checkint();

            try {
                int val = mars.Globals.memory.getHalf(addr);
                return LuaValue.valueOf(val);
            } catch (AddressErrorException var5) {
                System.err.printf("loadh(): address error: %#x%n", new Object[]{Integer.valueOf(addr)});
                return LuaValue.NIL;
            }
        }
    }

    static class LuaFunc_loadw extends OneArgFunction {
        public LuaValue call(LuaValue lvAddr) {
            int addr = lvAddr.checkint();

            try {
                int val = mars.Globals.memory.getWord(addr);
                return LuaValue.valueOf(val);
            } catch (AddressErrorException var5) {
                System.err.printf("loadw(): address error: %#x%n", new Object[]{Integer.valueOf(addr)});
                return LuaValue.NIL;
            }
        }
    }

    // TODO: valid check
    static class LuaFunc_register_instruction extends VarArgFunction {
        public Varargs onInvoke(Varargs args) {
            String template = args.arg(1).checkjstring();
            String formatStr = args.arg(2).checkjstring();
            String encoding = args.arg(3).checkjstring();
            final LuaFunction func = args.arg(4).checkfunction();
            BasicInstructionFormat format = null;

            // for java 6
            if(formatStr.equals("I_BRANCH")) {
                format = BasicInstructionFormat.I_BRANCH_FORMAT;
            }
            else if(formatStr.equals("I")) {
                format = BasicInstructionFormat.I_FORMAT;
            }
            else if(formatStr.equals("J")) {
                format = BasicInstructionFormat.J_FORMAT;
            }
            else if(formatStr.equals("R")) {
                format = BasicInstructionFormat.R_FORMAT;
            }

            SimulationCode code = new SimulationCode() {
                public void simulate(ProgramStatement statement) throws ProcessingException {
                    int[] operands = statement.getOperands();
                    int n = operands.length;
                    LuaValue[] args = new LuaValue[n];

                    for(int i = 0; i < n; ++i) {
                        args[i] = LuaNumber.valueOf(operands[i]);
                    }

                    func.invoke(args);
                }
            };
            mars.Globals.instructionSet.registerInstruction(template, format, encoding, code);
            return LuaValue.NIL;
        }
    }

    static class LuaFunc_setgpr extends TwoArgFunction {
        public LuaValue call(LuaValue lvNum, LuaValue lvVal) {
            int num = lvNum.checkint();
            int val = lvVal.checkint();
            RegisterFile.updateRegister(num, val);
            return LuaValue.NIL;
        }
    }

    static class LuaFunc_storeb extends TwoArgFunction {
        public LuaValue call(LuaValue lvAddr, LuaValue lvVal) {
            int addr = lvAddr.checkint();
            int val = lvVal.checkint();

            try {
                val = mars.Globals.memory.setByte(addr, val);
                return LuaValue.valueOf(val);
            } catch (AddressErrorException var6) {
                System.err.printf("storeb(): address error: %#x%n", new Object[]{Integer.valueOf(addr)});
                return LuaValue.NIL;
            }
        }
    }

    static class LuaFunc_storeh extends TwoArgFunction {
        public LuaValue call(LuaValue lvAddr, LuaValue lvVal) {
            int addr = lvAddr.checkint();
            int val = lvVal.checkint();

            try {
                val = mars.Globals.memory.setHalf(addr, val);
                return LuaValue.valueOf(val);
            } catch (AddressErrorException var6) {
                System.err.printf("storeh(): address error: %#x%n", new Object[]{Integer.valueOf(addr)});
                return LuaValue.NIL;
            }
        }
    }

    static class LuaFunc_storew extends TwoArgFunction {
        public LuaValue call(LuaValue lvAddr, LuaValue lvVal) {
            int addr = lvAddr.checkint();
            int val = lvVal.checkint();

            try {
                val = mars.Globals.memory.setWord(addr, val);
                return LuaValue.valueOf(val);
            } catch (AddressErrorException var6) {
                System.err.printf("storew(): address error: %#x%n", new Object[]{Integer.valueOf(addr)});
                return LuaValue.NIL;
            }
        }
    }
}
