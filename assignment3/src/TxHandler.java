import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TxHandler {

    private UTXOPool _current_utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
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

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        //Transaction[] acceptedTxs = new Transaction[];
        ArrayList<Integer> validTxs = new ArrayList<Integer>();
        for (int i = 0; i < possibleTxs.length; ++i) {
            if (isValidTx(possibleTxs[i])) {
                for (int j = 0; j < possibleTxs[i].numInputs(); j++) {
                    UTXO utxo = new UTXO(possibleTxs[i].getInput(j).prevTxHash, possibleTxs[i].getInput(j).outputIndex);
                    _current_utxoPool.removeUTXO(utxo);
                }
                for (int j = 0; j < possibleTxs[i].numOutputs(); j++) {
                    UTXO utxo = new UTXO(possibleTxs[i].getHash(), j);
                    _current_utxoPool.addUTXO(utxo, possibleTxs[i].getOutput(j));
                }
                validTxs.add(i);
            }
        }
        Transaction[] selectedTxs = new Transaction[validTxs.size()];
        for (int i = 0; i < validTxs.size(); i++) {
            selectedTxs[i] = possibleTxs[validTxs.get(i)];
        }
        return selectedTxs;
    }
}
