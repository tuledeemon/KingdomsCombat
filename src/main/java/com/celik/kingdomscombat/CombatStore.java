package com.celik.kingdomscombat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public final class CombatStore {
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private CombatStore(){}
    public static synchronized <T>T read(Path file,Class<T> type,T fallback){try{if(!Files.exists(file))return fallback;T value=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),type);if(value==null)throw new IllegalStateException("Empty combat file: "+file);return value;}catch(IOException e){throw new IllegalStateException("Cannot read combat file: "+file,e);}}
    /** Serialize the whole transaction, including encoding, and never share a staging file. */
    public static synchronized void write(Path file,Object value){
        Path target=file.toAbsolutePath().normalize();
        Path temporary=null;
        Throwable failure=null;
        try{
            Files.createDirectories(target.getParent());
            byte[] bytes=(JSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);
            temporary=Files.createTempFile(target.getParent(),"."+target.getFileName()+"-",".tmp");
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){
            var error=new IllegalStateException("Cannot save combat state: "+target,e);failure=error;throw error;
        }catch(RuntimeException|Error e){failure=e;throw e;}
        finally{
            // Delete only this transaction's file. Preserve any legacy .tmp file for recovery.
            if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException e){
                if(failure!=null)failure.addSuppressed(e);
                else KingdomsCombat.LOG.warn("Could not clean temporary combat file {}",temporary,e);
            }
        }
    }
}
