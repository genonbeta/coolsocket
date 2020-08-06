package org.monora.coolsocket.core;

import org.junit.Assert;
import org.junit.Test;
import org.monora.coolsocket.core.response.SizeMismatchException;
import org.monora.coolsocket.core.session.ActiveConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

public class DataTransactionTest
{
    public static final int PORT = 3789;

    @Test(timeout = 3000)
    public void readWritesToAnyOutputTest() throws IOException, InterruptedException
    {
        final String message = "The quick brown fox jumped over the lazy dog!\nThe quick brown fox jumped over " +
                "the lazy dog!\nThe quick brown fox jumped over the lazy dog!\nThe quick brown fox jumped over " +
                "the lazy dog!\nThe quick brown fox jumped over the lazy dog!\n";

        final InputStream inputStream = new ByteArrayInputStream(message.getBytes());

        CoolSocket coolSocket = new CoolSocket(PORT)
        {
            @Override
            public void onConnected(ActiveConnection activeConnection)
            {
                try {
                    ActiveConnection.Description description = activeConnection.writeBegin(0, LENGTH_UNSPECIFIED);
                    int len;

                    while ((len = inputStream.read(description.buffer)) != -1)
                        activeConnection.write(description, 0, len);

                    activeConnection.writeEnd(description);

                    // do the above with shortcuts
                    inputStream.reset();
                    activeConnection.reply(0, inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        coolSocket.start();

        int len;
        ActiveConnection activeConnection = ActiveConnection.connect(new InetSocketAddress(PORT));
        ActiveConnection.Description description = activeConnection.readBegin();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        while ((len = activeConnection.read(description)) != -1)
            outputStream.write(description.buffer, 0, len);

        Assert.assertEquals("The messages should match.", message, outputStream.toString());

        // do the above with shortcuts
        Assert.assertEquals("The messages should match.", message, activeConnection.receive().getAsString());

        activeConnection.close();
        coolSocket.stop();
    }

    @Test(expected = SizeMismatchException.class)
    public void exceedingInternalCacheGivesError() throws IOException, InterruptedException
    {
        CoolSocket coolSocket = new CoolSocket(PORT)
        {
            @Override
            public void onConnected(ActiveConnection activeConnection)
            {
                try {
                    ActiveConnection.Description description = activeConnection.writeBegin(0, LENGTH_UNSPECIFIED);
                    activeConnection.write(description, description.buffer);
                    activeConnection.writeEnd(description);
                } catch (IOException ignored) {

                }
            }
        };

        coolSocket.start();

        ActiveConnection activeConnection = ActiveConnection.connect(new InetSocketAddress(PORT));
        activeConnection.setInternalCacheLimit(100);

        try {
            activeConnection.receive();
        } finally {
            activeConnection.close();
            coolSocket.stop();
        }
    }

    @Test
    public void internalCacheLimitTest() throws InterruptedException, IOException
    {
        CoolSocket coolSocket = new CoolSocket(PORT)
        {
            @Override
            public void onConnected(ActiveConnection activeConnection)
            {
                try {
                    ActiveConnection.Description description = activeConnection.writeBegin(0, LENGTH_UNSPECIFIED);
                    activeConnection.write(description, description.buffer);
                    activeConnection.writeEnd(description);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        coolSocket.start();

        ActiveConnection activeConnection = ActiveConnection.connect(new InetSocketAddress(PORT));
        activeConnection.setInternalCacheLimit(8196);
        activeConnection.receive();
        activeConnection.close();
        coolSocket.stop();
    }

    @Test
    public void writesFixedDataTest() throws IOException, InterruptedException
    {
        final String message = "The quick brown fox jumped over the lazy dog!\nThe quick brown fox jumped over " +
                "the lazy dog!\nThe quick brown fox jumped over the lazy dog!\nThe quick brown fox jumped over " +
                "the lazy dog!\nThe quick brown fox jumped over the lazy dog!\n";

        final byte[] messageBytes = message.getBytes();
        final InputStream inputStream = new ByteArrayInputStream(messageBytes);

        CoolSocket coolSocket = new CoolSocket(PORT)
        {
            @Override
            public void onConnected(ActiveConnection activeConnection)
            {
                try {
                    activeConnection.reply(0, inputStream, messageBytes.length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        coolSocket.start();

        ActiveConnection activeConnection = ActiveConnection.connect(new InetSocketAddress(PORT));
        Assert.assertEquals("The messages should match.", message, activeConnection.receive().getAsString());
        activeConnection.close();
        coolSocket.stop();
    }

    @Test
    public void consumedInputStreamSendsZeroBytesTest() throws IOException, InterruptedException
    {
        final String message = "The quick brown fox jumped over the lazy dog!\nThe quick brown fox jumped over " +
                "the lazy dog!\nThe quick brown fox jumped over the lazy dog!\nThe quick brown fox jumped over " +
                "the lazy dog!\nThe quick brown fox jumped over the lazy dog!\n";
        final byte[] messageBytes = message.getBytes();
        final InputStream inputStream = new ByteArrayInputStream(messageBytes);

        CoolSocket coolSocket = new CoolSocket(PORT)
        {
            @Override
            public void onConnected(ActiveConnection activeConnection)
            {
                try {
                    if (inputStream.skip(messageBytes.length) != messageBytes.length)
                        throw new IOException("It did not skip bytes");
                    activeConnection.reply(0, inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        coolSocket.start();

        ActiveConnection activeConnection = ActiveConnection.connect(new InetSocketAddress(PORT));
        Assert.assertEquals("The message length should be zero.", 0, activeConnection.receive().length);

        activeConnection.close();
        coolSocket.stop();
    }
}
