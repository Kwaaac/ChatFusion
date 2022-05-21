package main.java.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public record Message(String login, String msg) {
        @Override
        public String toString() {
            return "[" + login + "]: " + msg;
        }

        public int length(Charset cs) {
            return login.getBytes(cs).length + msg.getBytes(cs).length + Integer.BYTES * 2;
        }

        /**
         * Return a buffer in readMode
         *
         * @param cs charset to encode the message
         * @return buffer in readMode containing the message
         */
        public ByteBuffer encode(Charset cs) {
            var buffer = ByteBuffer.allocate(this.length(cs));
            return buffer.putInt(login.getBytes(cs).length).put(cs.encode(login)).putInt(msg.getBytes(cs).length).put(cs.encode(msg)).flip();
        }
    }
