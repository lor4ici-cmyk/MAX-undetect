import java.nio.file.*;
import com.android.tools.smali.baksmali.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
public class Decode {
    public static void main(String[] args) throws Exception {
        DexBackedDexFile dex = new DexBackedDexFile(new Opcodes(35, -1), Files.readAllBytes(Paths.get(args[0])), 0);
        BaksmaliOptions options = new BaksmaliOptions(); options.localsDirective = true;
        Baksmali.disassembleDexFile(dex, new java.io.File(args[1]), 2, options);
    }
}
