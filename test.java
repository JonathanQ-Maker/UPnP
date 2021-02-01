public class test {
    public static void main(String[] args) {
        boolean success = UPnP.openTCPPort(25578);
        System.out.println(success);
    }
}
