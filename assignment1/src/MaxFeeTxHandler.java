import java.util.*;

public class MaxFeeTxHandler {

    private UTXOPool _current_utxoPool;
    private Transaction[] _max_transaction;
    private double _max_fee;
    private UTXOPool _max_utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        _current_utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        Set<UTXO> seen_utxo = new HashSet<>();
        double sum_in = 0;
        double sum_out = 0;
        for (int i = 0; i < tx.numInputs(); ++i) {
            UTXO utxo = new UTXO(tx.getInput(i).prevTxHash, tx.getInput(i).outputIndex);
            if (seen_utxo.contains(utxo)) {
                return false;
            }
            seen_utxo.add(utxo);
            if (! _current_utxoPool.contains(utxo)) {
                return false;
            }
            if (!Crypto.verifySignature(_current_utxoPool.getTxOutput(utxo).address, tx.getRawDataToSign(i),tx.getInput(i).signature)) {
                return false;
            }
            sum_in += _current_utxoPool.getTxOutput(utxo).value;
        }

        for (int i = 0; i < tx.numOutputs(); ++i) {
            if (tx.getOutput(i).value < 0) {
                return false;
            }
            sum_out += tx.getOutput(i).value;
        }

        return !(sum_in < sum_out);
    }

    public double isValidTx(Transaction tx, UTXOPool pool) {
        // IMPLEMENT THIS
        Set<UTXO> seen_utxo = new HashSet<>();
        double sum_in = 0;
        double sum_out = 0;
        for (int i = 0; i < tx.numInputs(); ++i) {
            UTXO utxo = new UTXO(tx.getInput(i).prevTxHash, tx.getInput(i).outputIndex);
            if (seen_utxo.contains(utxo)) {
                return -1;
            }
            seen_utxo.add(utxo);
            if (! pool.contains(utxo)) {
                return -1;
            }
            if (!Crypto.verifySignature(pool.getTxOutput(utxo).address, tx.getRawDataToSign(i),tx.getInput(i).signature)) {
                return -1;
            }
            sum_in += pool.getTxOutput(utxo).value;
        }

        for (int i = 0; i < tx.numOutputs(); ++i) {
            if (tx.getOutput(i).value < 0) {
                return -1;
            }
            sum_out += tx.getOutput(i).value;
        }

        return sum_in - sum_out;
    }

    public HashMap<UTXO, Transaction.Output> DoTx(UTXOPool ori, Transaction tx) {
        HashMap<UTXO, Transaction.Output> backupH = new HashMap<>();
        for (int j = 0; j < tx.numInputs(); j++) {
            UTXO utxo = new UTXO(tx.getInput(j).prevTxHash, tx.getInput(j).outputIndex);
            backupH.put(utxo, ori.getTxOutput(utxo));
            ori.removeUTXO(utxo);
        }
        for (int j = 0; j < tx.numOutputs(); j++) {
            UTXO utxo = new UTXO(tx.getHash(), j);
            ori.addUTXO(utxo, tx.getOutput(j));
        }
        return backupH;
    }

    public void UndoTx(UTXOPool ori, Transaction tx, HashMap<UTXO, Transaction.Output> backupH) {
        for (int j = 0; j < tx.numInputs(); j++) {
            UTXO utxo = new UTXO(tx.getInput(j).prevTxHash, tx.getInput(j).outputIndex);
            ori.addUTXO(utxo, backupH.get(utxo));
        }
        for (int j = 0; j < tx.numOutputs(); j++) {
            UTXO utxo = new UTXO(tx.getHash(), j);
            ori.removeUTXO(utxo);
        }
    }

    public boolean FindMaxFeeTxs(ArrayList<Integer> arr, UTXOPool pool, Transaction[] possibleTxs, double currentFee) {
        double thisFee = 0;
        if (arr.isEmpty() || (thisFee = isValidTx(possibleTxs[arr.get(arr.size()-1)], pool)) >= 0) {
            //explore
            boolean ifSuccessor = false;
            HashMap<UTXO, Transaction.Output> backupH = null;
            if (!arr.isEmpty()) {
                backupH = DoTx(pool, possibleTxs[arr.get(arr.size() - 1)]);
            }

            for (int i = 0; i < possibleTxs.length; i++) {
                if (!arr.contains(i)) {
                    arr.add(i);
                    ifSuccessor = FindMaxFeeTxs(arr, pool, possibleTxs, currentFee+thisFee) || ifSuccessor;
                    arr.remove(arr.size()-1);
                }
            }

            if (!ifSuccessor) {
                if (thisFee + currentFee > _max_fee) {
                    _max_transaction = new Transaction[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        _max_transaction[i] = possibleTxs[arr.get(i)];
                    }
                    _max_utxoPool = new UTXOPool(pool);
                    _max_fee = thisFee + currentFee;
                }
            }
            if (!arr.isEmpty()) {
                UndoTx(pool, possibleTxs[arr.get(arr.size() - 1)], backupH);
            }

            return true;

        } else {
            return false;
        }
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        _max_fee = -1;
        FindMaxFeeTxs(new ArrayList<Integer>(), _current_utxoPool, possibleTxs, 0);
        _current_utxoPool = _max_utxoPool;

        return _max_transaction;
    }

}
