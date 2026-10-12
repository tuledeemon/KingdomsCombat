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
    public static <T>T read(Path file,Class<T> type,T fallback){try{if(!Files.exists(file))return fallback;T value=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),type);if(value==null)throw new IllegalStateException("Empty combat file: "+file);return value;}catch(IOException e){throw new IllegalStateException("Cannot read combat file: "+file,e);}}
    public static void write(Path file,Object value){
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        try{Files.createDirectories(file.getParent());byte[] bytes=(JSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}}catch(IOException e){throw new IllegalStateException("Cannot save combat state: "+file,e);}
    }
}
