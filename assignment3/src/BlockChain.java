// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    //a hash table for add
    private TreeMap<Integer, List<Block>> block_height_list_map;
    private HashMap<byte[], Block> block_hash_map;
    private HashMap<byte[], UTXOPool> block_utxopool_map;
    private HashMap<byte[], Integer> block_height_map;
    private TransactionPool transaction_pool;
    private int max_height;  //height max, max - 1 ... (max - cut_off) can be maintain

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        // IMPLEMENT THIS
        int genesis_height = 1;

        max_height = genesis_height;
        block_height_list_map = new TreeMap<>();
        block_hash_map = new HashMap<>();
        block_utxopool_map = new HashMap<>();
        block_height_map = new HashMap<>();
        transaction_pool = new TransactionPool();

        List<Block> v_list =  new ArrayList<>();
        v_list.add(genesisBlock);
        block_height_list_map.put(genesis_height, v_list);
        block_hash_map.put(genesisBlock.getHash(), genesisBlock);
        block_height_map.put(genesisBlock.getHash(), genesis_height);

        //todo: init value to coinbase
        ArrayList<Transaction> transactions =  genesisBlock.getTransactions();
        TxHandler tx_handler = new TxHandler(new UTXOPool());
        tx_handler.handleTxs(transactions.toArray(new Transaction[transactions.size()]));
        UTXOPool handled_utxopool = tx_handler.getUTXOPool();
        handled_utxopool.addUTXO(new UTXO(genesisBlock.getCoinbase().getHash(), 0), genesisBlock.getCoinbase().getOutput(0));
        block_utxopool_map.put(genesisBlock.getHash(), handled_utxopool);
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        // IMPLEMENT THIS
        List<Block> v_list = block_height_list_map.get(max_height);
        return v_list.get(0);
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        // IMPLEMENT THIS
        List<Block> v_list = block_height_list_map.get(max_height);
        byte[] hash = v_list.get(0).getHash();
        return block_utxopool_map.get(hash);
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        // IMPLEMENT THIS
        TransactionPool ret_pool = transaction_pool;
        transaction_pool = new TransactionPool();
        return ret_pool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        // IMPLEMENT THIS
        // reject genesis block
        if (block.getHash() == null)
            return false;

        //// validate the block
        // previous not exist, or prune by cut off
        if (! block_hash_map.containsKey(block.getPrevBlockHash()))
            return false;

        // validate the transaction
        byte[] previous_hash = block_hash_map.get(block.getPrevBlockHash()).getHash();
        UTXOPool previous_utxopool = block_utxopool_map.get(previous_hash);
        ArrayList<Transaction> transactions =  block.getTransactions();
        TxHandler tx_handler = new TxHandler(previous_utxopool);
        Transaction [] handled_txs = tx_handler.handleTxs(transactions.toArray(new Transaction[transactions.size()]));
        if (handled_txs.length != transactions.size())
            return false;

        //// add the block
        int previous_height = block_height_map.get(previous_hash);
        int my_height = previous_height + 1;
        List<Block> v_list = block_height_list_map.get(my_height);
        if (v_list == null) {
            v_list =  new ArrayList<>();
        }
        v_list.add(block);
        block_height_list_map.put(my_height, v_list);
        block_hash_map.put(block.getHash(), block);
        block_height_map.put(block.getHash(), my_height);
        UTXOPool handled_utxopool = tx_handler.getUTXOPool();
        handled_utxopool.addUTXO(new UTXO(block.getCoinbase().getHash(), 0), block.getCoinbase().getOutput(0));
        block_utxopool_map.put(block.getHash(), handled_utxopool);
        // remove local pool transactions
        for (Transaction t: transactions) {
            transaction_pool.removeTransaction(t.getHash());
        }


        // try trim
        if (my_height > max_height) {
            int maintain_threshold = my_height - CUT_OFF_AGE;
            List<Integer> remove_heights = new ArrayList<>();
            for (Map.Entry<Integer, List<Block>> m: block_height_list_map.entrySet()) {
                if (m.getKey() >= maintain_threshold) {
                    break;
                }
                remove_heights.add(m.getKey());
            }

            for (Integer e: remove_heights) {
                List<Block> remove_blocks = block_height_list_map.get(e);
                for (Block b: remove_blocks) {
                    byte[] hash = b.getHash();
                    block_hash_map.remove(hash);
                    block_utxopool_map.remove(hash);
                    block_height_map.remove(hash);
                }
                block_height_list_map.remove(e);
            }

            max_height = my_height;
        }

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        // IMPLEMENT THIS
        transaction_pool.addTransaction(tx);
    }
}