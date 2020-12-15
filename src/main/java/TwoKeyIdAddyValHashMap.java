import java.util.HashMap;
import java.util.Set;

public class TwoKeyIdAddyValHashMap extends HashMap<Integer, PlayerData> {
    HashMap<String, Integer> keyRelation1 = new HashMap<>();

    @Override
    public PlayerData get(Object key) {
        if(key instanceof String) {
            return super.get(keyRelation1.get(key));
        } else {
            return super.get(key);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        if(key instanceof String) {
            return super.containsKey(keyRelation1.get(key));
        } else if(key instanceof Integer) {
            return super.containsKey(key);
        } else {
            return false;
        }
    }

    @Override
    public PlayerData put(Integer key, PlayerData value) {
        keyRelation1.put(value.addy, key);
        return super.put(key, value);
    }

    @Override
    public PlayerData remove(Object key) {
        if(key instanceof Integer) {
            return super.remove(key);
        } else if (key instanceof String) {
            return super.remove(keyRelation1.get(key));
        } else {
            return null;
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        if(key instanceof Integer) {
            return super.remove(key, value);
        } else if (key instanceof String) {
            return super.remove(keyRelation1.get(key), value);
        } else {
            return false;
        }
    }

    public String getAllPlayerPing() {
        Set<Integer> arrayList = super.keySet();
        StringBuilder result = new StringBuilder();
        for (int i : arrayList) {
           result.append("@").append(super.get(i).username).append("\n");
        }
        return result.toString();
    }

    public String getAllPlayerPingWithShots() {
        Set<Integer> arrayList = super.keySet();
        StringBuilder result = new StringBuilder();
        for (int i : arrayList) {
            result.append("@").append(get(i).username).append(" : x").append(get(i).shotsToFire).append("Shots\n\n");
        }
        return result.toString();
    }

    public boolean didAllPlayerPay() {
        Set<Integer> arrayList = super.keySet();
        boolean result = true;
        for (int i : arrayList) {
            result = result && get(i).didPlayerPay;
        }
        return result;
    }
}
