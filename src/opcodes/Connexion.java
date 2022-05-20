public enum Connexion {
    LOGIN_ANONYMOUS(0), LOGIN_PASSWORD(1), LOGIN_ACCEPTED(2), LOGIN_REFUSED(3);


    private static final HashMap<Integer, Connexion> codeMap = new HashMap();

    static {
        for (var conn : Connexion.values()) {
            codeMap.put(conn.opCode, conn);
        }
    }

    private final int opCode;


    private Connexion(int opCode) {
        this.opCode = opCode;
    }

    public static Optional<Connexion> getConnexionFromOpCode(int opcode) {
        var conn = codeMap.get(opcode);
        return conn == null ? Optional.empty() : Optional.of(conn);
    }

}