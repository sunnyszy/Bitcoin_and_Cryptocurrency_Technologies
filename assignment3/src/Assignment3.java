import java.lang.reflect.InvocationTargetException;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class Assignment3 {
	private class ForwardBlockNode {
		public Block b;
		public ForwardBlockNode child;

		public ForwardBlockNode(Block b) {
			this.b = b;
			this.child = null;
		}

		public void setChild(ForwardBlockNode child) {
			this.child = child;
		}
	}

	public int nPeople;
	public int nUTXOTx;
	public int maxUTXOTxOutput;
	public double maxValue;
	public int nTxPerTest;
	public int maxInput;
	public int maxOutput;

	public ArrayList<KeyPair> people;

	public Assignment3() {
		this.nPeople = 20;
		this.nUTXOTx = 20;
		this.maxUTXOTxOutput = 20;
		this.maxValue = 10;
		this.nTxPerTest = 50;
		this.maxInput = 4;
		this.maxOutput = 20;

		byte[] key = new byte[32];
		for (int i = 0; i < 32; i++) {
			key[i] = (byte) 1;
		}

		people = new ArrayList<KeyPair>();
		for (int i = 0; i < nPeople; i++) {
			KeyPairGenerator keyGen;
			try {
				keyGen = KeyPairGenerator.getInstance("RSA");
				keyGen.initialize(512);
				KeyPair pair = keyGen.genKeyPair();
				people.add(pair);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private byte[] signMessage(PrivateKey privateKey, byte[] message) {
		Signature sig;
		try {
			sig = Signature.getInstance("SHA256withRSA");
			sig.initSign(privateKey);
			sig.update(message);
			byte[] sigBytes = sig.sign();
			return sigBytes;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SignatureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private void printPassFail(int testNo, boolean passed) {
		String resStr = passed ? "PASSED" : "FAILED";
		System.out.println("Test " + Integer.toString(testNo) + ": " + resStr);
	}

	/**
	 * Test 1: Process a block with no transactions
	 * 
	 * @return
	 */
	public boolean test1() {
		System.out.println("Test 1: Process a block with no transactions");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		block.finalize();
		boolean passes = blockHandler.processBlock(block);
		printPassFail(1, passes);
		return passes;
	}

	/**
	 * Test 2: Process a block with a single valid transaction
	 * 
	 */
	public boolean test2() {
		System.out.println("Test 2: Process a block with a single valid transaction");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		boolean passes = blockHandler.processBlock(block);
		printPassFail(2, passes);
		return passes;
	}

	/**
	 * Test 3: Process a block with many valid transactions
	 * 
	 * @return
	 */
	public boolean test3() {
		System.out.println("Test 3: Process a block with many valid transactions");
		boolean passes = true;

		for (int k = 0; k < 20; k++) {
			Block genesisBlock = new Block(null, people.get(0).getPublic());
			genesisBlock.finalize();

			BlockChain blockChain = new BlockChain(genesisBlock);
			BlockHandler blockHandler = new BlockHandler(blockChain);

			Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
			Transaction spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);

			double totalValue = 0;
			UTXOPool utxoPool = new UTXOPool();
			int numOutputs = 0;
			HashMap<UTXO, KeyPair> utxoToKeyPair = new HashMap<UTXO, KeyPair>();
			HashMap<Integer, KeyPair> keyPairAtIndex = new HashMap<Integer, KeyPair>();

			for (int j = 0; j < maxUTXOTxOutput; j++) {
				Random ran = new Random();
				int rIndex = ran.nextInt(people.size());// SampleRandom.randomInt(people.size());
				PublicKey addr = people.get(rIndex).getPublic();

				double value = ran.nextDouble() * maxValue;
				if (totalValue + value > Block.COINBASE)
					break;
				spendCoinbaseTx.addOutput(value, addr);
				keyPairAtIndex.put(j, people.get(rIndex));
				totalValue += value;
				numOutputs++;
			}

			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);

			for (int j = 0; j < numOutputs; j++) {
				UTXO ut = new UTXO(spendCoinbaseTx.getHash(), j);
				utxoPool.addUTXO(ut, spendCoinbaseTx.getOutput(j));
				utxoToKeyPair.put(ut, keyPairAtIndex.get(j));
			}

			ArrayList<UTXO> utxoSet = utxoPool.getAllUTXO();
			HashSet<UTXO> utxosSeen = new HashSet<UTXO>();
			int maxValidInput = Math.min(maxInput, utxoSet.size());

			for (int i = 0; i < nTxPerTest; i++) {
				Transaction tx = new Transaction();
				HashMap<Integer, UTXO> utxoAtIndex = new HashMap<Integer, UTXO>();
				Random ran = new Random();
				int nInput = ran.nextInt(maxValidInput) + 1;
				int numInputs = 0;
				double inputValue = 0;
				for (int j = 0; j < nInput; j++) {
					UTXO utxo = utxoSet.get(ran.nextInt(utxoSet.size()));
					if (!utxosSeen.add(utxo)) {
						j--;
						nInput--;
						continue;
					}
					tx.addInput(utxo.getTxHash(), utxo.getIndex());
					inputValue += utxoPool.getTxOutput(utxo).value;
					utxoAtIndex.put(j, utxo);
					numInputs++;
				}

				if (numInputs == 0)
					continue;

				int nOutput = ran.nextInt(maxOutput) + 1;
				double outputValue = 0;
				for (int j = 0; j < nOutput; j++) {
					double value = ran.nextDouble() * maxValue;
					if (outputValue + value > inputValue)
						break;
					int rIndex = ran.nextInt(people.size());
					PublicKey addr = people.get(rIndex).getPublic();
					tx.addOutput(value, addr);
					outputValue += value;
				}
				for (int j = 0; j < numInputs; j++) {
					byte[] sig = signMessage(utxoToKeyPair.get(utxoAtIndex.get(j)).getPrivate(),
							tx.getRawDataToSign(j));
					tx.addSignature(sig, j);
				}
				tx.finalize();
				block.addTransaction(tx);
			}

			block.finalize();

			passes = passes && blockHandler.processBlock(block);
		}
		printPassFail(3, passes);
		return passes;
	}

	/**
	 * Test 4: Process a block with some double spends
	 * 
	 * @return
	 */
	public boolean test4() {
		System.out.println("Test 4: Process a block with some double spends");

		boolean passes = true;

		for (int k = 0; k < 20; k++) {
			Block genesisBlock = new Block(null, people.get(0).getPublic());
			genesisBlock.finalize();

			BlockChain blockChain = new BlockChain(genesisBlock);
			BlockHandler blockHandler = new BlockHandler(blockChain);

			Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
			Transaction spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);

			double totalValue = 0;
			UTXOPool utxoPool = new UTXOPool();
			int numOutputs = 0;
			HashMap<UTXO, KeyPair> utxoToKeyPair = new HashMap<UTXO, KeyPair>();
			HashMap<Integer, KeyPair> keyPairAtIndex = new HashMap<Integer, KeyPair>();

			for (int j = 0; j < maxUTXOTxOutput; j++) {
				Random rand = new Random();
				int rIndex = rand.nextInt(people.size());
				PublicKey addr = people.get(rIndex).getPublic();
				double value = rand.nextDouble() * maxValue;
				if (totalValue + value > Block.COINBASE)
					break;
				spendCoinbaseTx.addOutput(value, addr);
				keyPairAtIndex.put(j, people.get(rIndex));
				totalValue += value;
				numOutputs++;
			}

			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);

			for (int j = 0; j < numOutputs; j++) {
				UTXO ut = new UTXO(spendCoinbaseTx.getHash(), j);
				utxoPool.addUTXO(ut, spendCoinbaseTx.getOutput(j));
				utxoToKeyPair.put(ut, keyPairAtIndex.get(j));
			}

			ArrayList<UTXO> utxoSet = utxoPool.getAllUTXO();
			HashSet<UTXO> utxosSeen = new HashSet<UTXO>();
			int maxValidInput = Math.min(maxInput, utxoSet.size());

			boolean notCorrupted = true;

			for (int i = 0; i < nTxPerTest; i++) {
				Transaction tx = new Transaction();
				HashMap<Integer, UTXO> utxoAtIndex = new HashMap<Integer, UTXO>();
				Random rand = new Random();
				int nInput = rand.nextInt(maxValidInput) + 1;
				int numInputs = 0;
				double inputValue = 0;
				for (int j = 0; j < nInput; j++) {
					UTXO utxo = utxoSet.get(rand.nextInt(utxoSet.size()));
					if (!utxosSeen.add(utxo)) {
						notCorrupted = false;
					}
					tx.addInput(utxo.getTxHash(), utxo.getIndex());
					inputValue += utxoPool.getTxOutput(utxo).value;
					utxoAtIndex.put(j, utxo);
					numInputs++;
				}

				if (numInputs == 0)
					continue;

				int nOutput = rand.nextInt(maxOutput) + 1;
				double outputValue = 0;
				for (int j = 0; j < nOutput; j++) {
					double value = rand.nextDouble() * maxValue;
					if (outputValue + value > inputValue)
						break;
					int rIndex = rand.nextInt(people.size());
					PublicKey addr = people.get(rIndex).getPublic();
					tx.addOutput(value, addr);
					outputValue += value;
				}
				for (int j = 0; j < numInputs; j++) {
					tx.addSignature(
							signMessage(utxoToKeyPair.get(utxoAtIndex.get(j)).getPrivate(), tx.getRawDataToSign(j)), j);
				}
				tx.finalize();
				block.addTransaction(tx);
			}

			block.finalize();

			passes = passes && (blockHandler.processBlock(block) == notCorrupted);
		}

		printPassFail(4, passes);
		return passes;
	}

	/**
	 * Test5: Process a new genesis block
	 * 
	 * @return
	 */
	public boolean test5() {
		System.out.println("Test 5: Process a new genesis block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block genesisblock = new Block(null, people.get(1).getPublic());
		genesisblock.finalize();

		boolean passes = !blockHandler.processBlock(genesisblock);
		printPassFail(5, passes);
		return passes;
	}

	/**
	 * Test6: Process a block with an invalid prevBlockHash
	 * 
	 * @return
	 */
	public boolean test6() {
		System.out.println("Test 6: Process a block with an invalid prevBlockHash");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();
		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		byte[] hash = genesisBlock.getHash();
		byte[] hashCopy = Arrays.copyOf(hash, hash.length);
		hashCopy[0]++;
		Block block = new Block(hashCopy, people.get(1).getPublic());
		block.finalize();

		boolean passes = !blockHandler.processBlock(block);
		printPassFail(6, passes);
		return passes;
	}

	/**
	 * Test 7: Process blocks with different sorts of invalid transactions
	 * 
	 * @return
	 */
	public boolean test7() {
		System.out.println("Test 7: Process blocks with different sorts of invalid transactions");

		boolean passes = true;

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		byte[] rawData = spendCoinbaseTx.getRawDataToSign(0);
		rawData[0]++;
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), rawData), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(1).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE + 1, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		spendCoinbaseTx = new Transaction();
		byte[] hash = genesisBlock.getCoinbase().getHash();
		byte[] hashCopy = Arrays.copyOf(hash, hash.length);
		hashCopy[0]++;
		spendCoinbaseTx.addInput(hashCopy, 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 1);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(-Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);
		printPassFail(7, passes);
		return passes;
	}

	/**
	 * Test 8: Process multiple blocks directly on top of the genesis block
	 * 
	 * @return
	 */
	public boolean test8() {
		System.out.println("Test 8: Process multiple blocks directly on top of the genesis block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		Block block;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < 100; i++) {
			block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();
			passes = passes && blockHandler.processBlock(block);
		}

		printPassFail(8, passes);
		return passes;
	}

	/**
	 * Test 9: Process a block containing a transaction that claims a UTXO already
	 * claimed by a transaction in its parent
	 * 
	 * @return
	 */
	public boolean test9() {
		System.out.println(
				"Test 9: Process a block containing a transaction that claims a UTXO already claimed by a transaction in its parent");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		Block prevBlock = block;

		block = new Block(prevBlock.getHash(), people.get(2).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE - 1, people.get(2).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(1).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		printPassFail(9, passes);
		return passes;
	}

	/**
	 * Test 10: Process a block containing a transaction that claims a UTXO not on
	 * its branch
	 * 
	 * @return
	 */
	public boolean test10() {
		System.out.println("Test 10: Process a block containing a transaction that claims a UTXO not on its branch");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		Block prevBlock = block;

		block = new Block(genesisBlock.getHash(), people.get(2).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(2).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(1).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		printPassFail(10, passes); 
		return passes;
	}

	/**
	 * Test 11: Process a block containing a transaction that claims a UTXO from
	 * earlier in its branch that has not yet been claimed
	 * 
	 * @return
	 */
	public boolean test11() {
		System.out.println(
				"Test 11: Process a block containing a transaction that claims a UTXO from earlier in its branch that has not yet been claimed");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		Block prevBlock = block;
		Transaction retainTx = spendCoinbaseTx;

		block = new Block(prevBlock.getHash(), people.get(2).getPublic());
		spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(2).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(1).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		Block prevPrevBlock = prevBlock;
		prevBlock = block;

		block = new Block(prevBlock.getHash(), people.get(3).getPublic());
		Transaction spendOldUTXOTransaction = new Transaction();
		spendOldUTXOTransaction.addInput(retainTx.getHash(), 0);
		spendOldUTXOTransaction.addOutput(Block.COINBASE, people.get(2).getPublic());
		spendOldUTXOTransaction
				.addSignature(signMessage(people.get(1).getPrivate(), spendOldUTXOTransaction.getRawDataToSign(0)), 0);
		spendOldUTXOTransaction.finalize();
		block.addTransaction(spendOldUTXOTransaction);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		printPassFail(11, passes); 
		return passes;
	}

	/**
	 * Test 12: Process a linear chain of blocks
	 * 
	 * @return
	 */
	public boolean test12() {
		System.out.println("Test 12: Process a linear chain of blocks");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		Block block;
		Block prevBlock = genesisBlock;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < 100; i++) {
			block = new Block(prevBlock.getHash(), people.get(0).getPublic());
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();
			prevBlock = block;

			passes = passes && blockHandler.processBlock(block);
		}

		printPassFail(12, passes); 
		return passes;
	}

	/**
	 * Test13: Process a linear chain of blocks of length CUT_OFF_AGE and then a
	 * block on top of the genesis block
	 * 
	 * @return
	 */
	public boolean test13() {
		System.out.println(
				"Test 13: Process a linear chain of blocks of length CUT_OFF_AGE and then a block on top of the genesis block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		Block block;
		Block prevBlock = genesisBlock;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < BlockChain.CUT_OFF_AGE; i++) {
			block = new Block(prevBlock.getHash(), people.get(0).getPublic());
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();
			prevBlock = block;

			passes = passes && blockHandler.processBlock(block);
		}

		block = new Block(genesisBlock.getHash(), people.get(0).getPublic());
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		printPassFail(13, passes); 
		return passes;
	}

	/**
	 * Test 14: Process a linear chain of blocks of length CUT_OFF_AGE + 1 and then
	 * a block on top of the genesis block
	 * 
	 * @return
	 */
	public boolean test14() {
		System.out.println(
				"Test 14: Process a linear chain of blocks of length CUT_OFF_AGE + 1 and then a block on top of the genesis block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		Block block;
		Block prevBlock = genesisBlock;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < BlockChain.CUT_OFF_AGE + 1; i++) {
			block = new Block(prevBlock.getHash(), people.get(0).getPublic());
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();
			prevBlock = block;

			passes = passes && blockHandler.processBlock(block);
		}

		block = new Block(genesisBlock.getHash(), people.get(0).getPublic());
		block.finalize();

		passes = passes && !blockHandler.processBlock(block);

		printPassFail(14, passes); 
		return passes;
	}

	/**
	 * "Test 15: Create a block when no transactions have been processed
	 * 
	 * @return
	 */
	public boolean test15() {
		System.out.println("Test 15: Create a block when no transactions have been processed");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());
		
		boolean passes = createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash())
				&& createdBlock.getTransactions().size() == 0;
		printPassFail(15, passes);
		return passes;
	}

	/**
	 * Test 16: Create a block after a single valid transaction has been processed
	 * 
	 * @return
	 */
	public boolean test16() {
		System.out.println("Test 16: Create a block after a single valid transaction has been processed");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		blockHandler.processTx(spendCoinbaseTx);

		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());
		boolean passes = createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash())
				&& createdBlock.getTransactions().size() == 1
				&& createdBlock.getTransaction(0).equals(spendCoinbaseTx);
		printPassFail(16, passes);
		return passes;
	}

	/**
	 * Test 17: Create a block after a valid transaction has been processed, then
	 * create a second block
	 * 
	 * @return
	 */
	public boolean test17() {

		System.out.println(
				"Test 17: Create a block after a valid transaction has been processed, then create a second block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();
		// RSAPublicKey pk = people.get(0).getPublic();
		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, (PublicKey) people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		blockHandler.processTx(spendCoinbaseTx);

		Block createdBlock = blockHandler.createBlock((PublicKey) people.get(1).getPublic());
		Block createdBlock2 = blockHandler.createBlock((PublicKey) people.get(2).getPublic());
		boolean passes = createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash())
				&& createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx)
				&& createdBlock2 != null && createdBlock2.getPrevBlockHash().equals(createdBlock.getHash())
				&& createdBlock2.getTransactions().size() == 0;

		printPassFail(17, passes);
		return passes;
	}

	/**
	 * Test 18: Create a block after a valid transaction has been processed that is already
	 * in a block in the longest valid branch
	 * 
	 * @return
	 */
	public boolean test18() {
		System.out.println(
				"Test 18: Create a block after a valid transaction has been processed that is already in a block in the longest valid branch");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		blockHandler.processTx(spendCoinbaseTx);
		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());
		passes = passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(block.getHash())
				&& createdBlock.getTransactions().size() == 0;

		printPassFail(18, passes); 
		return passes;
	}

	/**
	 * Test 19: Create a block after a valid transaction has been processed that
	 * uses a UTXO already claimed by a transaction in the longest valid branch
	 * 
	 * @return
	 */
	public boolean test19() {
		System.out.println(
				"Test 19: Create a block after a valid transaction has been processed that uses a UTXO already claimed by a transaction in the longest valid branch");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		Transaction spendCoinbaseTx2 = new Transaction();
		spendCoinbaseTx2.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx2.addOutput(Block.COINBASE - 1, people.get(1).getPublic());
		spendCoinbaseTx2.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx2.getRawDataToSign(0)), 0);
		spendCoinbaseTx2.finalize();

		blockHandler.processTx(spendCoinbaseTx2);
		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());
		passes = passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(block.getHash())
				&& createdBlock.getTransactions().size() == 0;
		printPassFail(19, passes);
		return passes;
	}

	/**
	 * Test 20: Create a block after a valid transaction has been processed that is
	 * not a double spend on the longest valid branch and has not yet been included
	 * in any other block
	 * 
	 * @return
	 */
	public boolean test20() {
		System.out.println(
				"Test 20: Create a block after a valid transaction has been processed that is not a double spend on the longest valid branch and has not yet been included in any other block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE - 1, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.finalize();

		passes = passes && blockHandler.processBlock(block);

		Transaction spendPrevTx = new Transaction();
		spendPrevTx.addInput(block.getCoinbase().getHash(), 0);
		spendPrevTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendPrevTx.addSignature(signMessage(people.get(1).getPrivate(), spendPrevTx.getRawDataToSign(0)), 0);
		spendPrevTx.finalize();

		blockHandler.processTx(spendPrevTx);
		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());
		passes = passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(block.getHash())
				&& createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendPrevTx);

		printPassFail(20, passes); return passes;
	}

	/**
	 * Test 21: Create a block after only invalid transactions have been processed
	 * 
	 * @return
	 */
	public boolean test21() {
		System.out.println("Test 21: Create a block after only invalid transactions have been processed");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE + 2, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		blockHandler.processTx(spendCoinbaseTx);

		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());

		boolean passes = createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash())
				&& createdBlock.getTransactions().size() == 0;
		printPassFail(21, passes); return passes;
	}

	/**
	 * Test 22: Process a transaction, create a block, process a transaction, create
	 * a block, ...
	 * 
	 * @return
	 */
	public boolean test22() {
		System.out
				.println("Test 22: Process a transaction, create a block, process a transaction, create a block, ...");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		Transaction spendCoinbaseTx;
		Block prevBlock = genesisBlock;

		for (int i = 0; i < 20; i++) {
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			blockHandler.processTx(spendCoinbaseTx);

			Block createdBlock = blockHandler.createBlock(people.get(0).getPublic());

			passes = passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(prevBlock.getHash())
					&& createdBlock.getTransactions().size() == 1
					&& createdBlock.getTransaction(0).equals(spendCoinbaseTx);
			prevBlock = createdBlock;
		}
		printPassFail(22, passes);
		return passes;
	}

	/**
	 * Test 23: Process a transaction, create a block, then process a block on top
	 * of that block with a transaction claiming a UTXO from that transaction
	 * 
	 * @return
	 */
	public boolean test23() {
		System.out.println(
				"Test 23: Process a transaction, create a block, then process a block on top of that block with a transaction claiming a UTXO from that transaction");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		blockHandler.processTx(spendCoinbaseTx);

		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());

		Block newBlock = new Block(createdBlock.getHash(), people.get(1).getPublic());
		Transaction spendTx = new Transaction();
		spendTx.addInput(spendCoinbaseTx.getHash(), 0);
		spendTx.addOutput(Block.COINBASE, people.get(2).getPublic());
		spendTx.addSignature(signMessage(people.get(1).getPrivate(), spendTx.getRawDataToSign(0)), 0);
		spendTx.finalize();
		newBlock.addTransaction(spendTx);
		newBlock.finalize();

		boolean processNewBlock = blockHandler.processBlock(newBlock);
		boolean passes = createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash())
				&& createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx)
				&& processNewBlock;
		printPassFail(23, passes);
		return passes;

	}

	/**
	 * Test 24: Process a transaction, create a block, then process a block on top
	 * of the genesis block with a transaction claiming a UTXO from that transaction
	 * 
	 * @return
	 */
	public boolean test24() {
		System.out.println(
				"Test 24: Process a transaction, create a block, then process a block on top of the genesis block with a transaction claiming a UTXO from that transaction");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		blockHandler.processTx(spendCoinbaseTx);

		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());

		Block newBlock = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendTx = new Transaction();
		spendTx.addInput(spendCoinbaseTx.getHash(), 0);
		spendTx.addOutput(Block.COINBASE, people.get(2).getPublic());
		spendTx.addSignature(signMessage(people.get(1).getPrivate(), spendTx.getRawDataToSign(0)), 0);
		spendTx.finalize();
		newBlock.addTransaction(spendTx);
		newBlock.finalize();
		boolean passes = createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash())
				&& createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx)
				&& !blockHandler.processBlock(newBlock);
		printPassFail(24, passes); 
		return passes;

	}

	/**
	 * Test 25: Process multiple blocks directly on top of the genesis block, then
	 * create a block
	 * 
	 * @return
	 */
	public boolean test25() {
		System.out
				.println("Test 25: Process multiple blocks directly on top of the genesis block, then create a block");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		Block block;
		Block firstBlock = null;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < 100; i++) {
			block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
			if (i == 0)
				firstBlock = block;

			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();
			passes = passes && blockHandler.processBlock(block);
		}

		Block createdBlock = blockHandler.createBlock(people.get(1).getPublic());

		boolean passes2 = createdBlock != null && createdBlock.getPrevBlockHash().equals(firstBlock.getHash())
				&& createdBlock.getTransactions().size() == 0;

		printPassFail(25, passes2);
		return passes2;
	}

	/**
	 * Test 26: Construct two branches of approximately equal size, ensuring that
	 * blocks are always created on the proper branch
	 * 
	 * @return
	 */
	public boolean test26() {
		System.out.println(
				"Test 26: Construct two branches of approximately equal size, ensuring that blocks are always created on the proper branch");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		boolean flipped = false;
		Block block;
		Block firstBranchPrevBlock = genesisBlock;
		Block secondBranchPrevBlock = genesisBlock;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < 30; i++) {
			spendCoinbaseTx = new Transaction();
			if (i % 2 == 0) {
				if (!flipped) {
					spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublic());
					spendCoinbaseTx
							.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					blockHandler.processTx(spendCoinbaseTx);

					block = blockHandler.createBlock(people.get(0).getPublic());

					passes = passes && block != null && block.getPrevBlockHash().equals(firstBranchPrevBlock.getHash())
							&& block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
					firstBranchPrevBlock = block;
				} else {
					spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublic());
					spendCoinbaseTx
							.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					blockHandler.processTx(spendCoinbaseTx);

					block = blockHandler.createBlock(people.get(0).getPublic());

					passes = passes && block != null && block.getPrevBlockHash().equals(secondBranchPrevBlock.getHash())
							&& block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
					secondBranchPrevBlock = block;
				}
			} else {
				if (!flipped) {
					// add two blocks two second branch
					block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx
							.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					secondBranchPrevBlock = block;

					block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx
							.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					secondBranchPrevBlock = block;

					if (i > 1) {
						block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublic());
						spendCoinbaseTx = new Transaction();
						spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
						spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
						spendCoinbaseTx.addSignature(
								signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
						spendCoinbaseTx.finalize();
						block.addTransaction(spendCoinbaseTx);
						block.finalize();

						passes = passes && blockHandler.processBlock(block);
						secondBranchPrevBlock = block;
					}
				} else {
					block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx
							.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					firstBranchPrevBlock = block;

					block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx
							.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					firstBranchPrevBlock = block;

					if (i > 1) {
						block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublic());
						spendCoinbaseTx = new Transaction();
						spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
						spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
						spendCoinbaseTx.addSignature(
								signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
						spendCoinbaseTx.finalize();
						block.addTransaction(spendCoinbaseTx);
						block.finalize();

						passes = passes && blockHandler.processBlock(block);
						firstBranchPrevBlock = block;
					}
				}
				flipped = !flipped;
			}
		}
		printPassFail(26, passes); 
		return passes;
	}

	/**
	 * Test 27: Similar to previous test, but then try to process blocks whose
	 * parents are at height < maxHeight - CUT_OFF_AGE
	 * 
	 * @return
	 */
	public boolean test27() {
		System.out.println(
				"Test 27: Similar to previous test, but then try to process blocks whose parents are at height < maxHeight - CUT_OFF_AGE");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.finalize();

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		boolean passes = true;
		boolean flipped = false;
		Block block;
		Block firstBranchPrevBlock = genesisBlock;
		ForwardBlockNode firstBranch = new ForwardBlockNode(firstBranchPrevBlock);
		ForwardBlockNode firstBranchTracker = firstBranch;
		Block secondBranchPrevBlock = genesisBlock;
		ForwardBlockNode secondBranch = new ForwardBlockNode(secondBranchPrevBlock);
		ForwardBlockNode secondBranchTracker = secondBranch;
		Transaction spendCoinbaseTx;

		for (int i = 0; i < 3 * BlockChain.CUT_OFF_AGE; i++) {
			spendCoinbaseTx = new Transaction();
			if (i % 2 == 0) {
				if (!flipped) {
					spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublic());
					spendCoinbaseTx.addSignature(
							signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					blockHandler.processTx(spendCoinbaseTx);

					block = blockHandler.createBlock(people.get(0).getPublic());

					passes = passes && block != null && block.getPrevBlockHash().equals(firstBranchPrevBlock.getHash())
							&& block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
					ForwardBlockNode newNode = new ForwardBlockNode(block);
					firstBranchTracker.setChild(newNode);
					firstBranchTracker = newNode;
					firstBranchPrevBlock = block;
				} else {
					spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublic());
					spendCoinbaseTx.addSignature(
							signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					blockHandler.processTx(spendCoinbaseTx);

					block = blockHandler.createBlock(people.get(0).getPublic());

					passes = passes && block != null && block.getPrevBlockHash().equals(secondBranchPrevBlock.getHash())
							&& block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
					ForwardBlockNode newNode = new ForwardBlockNode(block);
					secondBranchTracker.setChild(newNode);
					secondBranchTracker = newNode;
					secondBranchPrevBlock = block;
				}
			} else {
				if (!flipped) {
					// add two blocks two second branch
					block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx.addSignature(
							signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					ForwardBlockNode newNode = new ForwardBlockNode(block);
					secondBranchTracker.setChild(newNode);
					secondBranchTracker = newNode;
					secondBranchPrevBlock = block;

					block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx.addSignature(
							signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					newNode = new ForwardBlockNode(block);
					secondBranchTracker.setChild(newNode);
					secondBranchTracker = newNode;
					secondBranchPrevBlock = block;

					if (i > 1) {
						block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublic());
						spendCoinbaseTx = new Transaction();
						spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
						spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
						spendCoinbaseTx.addSignature(
								signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
						spendCoinbaseTx.finalize();
						block.addTransaction(spendCoinbaseTx);
						block.finalize();

						passes = passes && blockHandler.processBlock(block);
						newNode = new ForwardBlockNode(block);
						secondBranchTracker.setChild(newNode);
						secondBranchTracker = newNode;
						secondBranchPrevBlock = block;
					}
				} else {
					block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx.addSignature(
							signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					ForwardBlockNode newNode = new ForwardBlockNode(block);
					firstBranchTracker.setChild(newNode);
					firstBranchTracker = newNode;
					firstBranchPrevBlock = block;

					block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublic());
					spendCoinbaseTx = new Transaction();
					spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
					spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
					spendCoinbaseTx.addSignature(
							signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
					spendCoinbaseTx.finalize();
					block.addTransaction(spendCoinbaseTx);
					block.finalize();

					passes = passes && blockHandler.processBlock(block);
					newNode = new ForwardBlockNode(block);
					firstBranchTracker.setChild(newNode);
					firstBranchTracker = newNode;
					firstBranchPrevBlock = block;

					if (i > 1) {
						block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublic());
						spendCoinbaseTx = new Transaction();
						spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
						spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
						spendCoinbaseTx.addSignature(
								signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
						spendCoinbaseTx.finalize();
						block.addTransaction(spendCoinbaseTx);
						block.finalize();

						passes = passes && blockHandler.processBlock(block);
						newNode = new ForwardBlockNode(block);
						firstBranchTracker.setChild(newNode);
						firstBranchTracker = newNode;
						firstBranchPrevBlock = block;
					}
				}
				flipped = !flipped;
			}
		}

		int firstBranchHeight = 0;
		firstBranchTracker = firstBranch;
		while (firstBranchTracker != null) {
			firstBranchTracker = firstBranchTracker.child;
			firstBranchHeight++;
		}

		int secondBranchHeight = 0;
		secondBranchTracker = secondBranch;
		while (secondBranchTracker != null) {
			secondBranchTracker = secondBranchTracker.child;
			secondBranchHeight++;
		}

		int maxHeight = Math.max(firstBranchHeight, secondBranchHeight);

		int firstBranchCount = 0;
		firstBranchTracker = firstBranch;
		while (firstBranchTracker.child != null) {
			block = new Block(firstBranchTracker.b.getHash(), people.get(0).getPublic());
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(firstBranchTracker.b.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();

			if (firstBranchCount < maxHeight - BlockChain.CUT_OFF_AGE - 1) {
				passes = passes && !blockHandler.processBlock(block);
			} else {
				passes = passes && blockHandler.processBlock(block);
			}

			firstBranchTracker = firstBranchTracker.child;
			firstBranchCount++;
		}

		int secondBranchCount = 0;
		secondBranchTracker = secondBranch;
		while (secondBranchTracker != null) {
			block = new Block(secondBranchTracker.b.getHash(), people.get(0).getPublic());
			spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(secondBranchTracker.b.getCoinbase().getHash(), 0);
			spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);
			block.finalize();

			if (secondBranchCount < maxHeight - BlockChain.CUT_OFF_AGE - 1) {
				passes = passes && !blockHandler.processBlock(block);
			} else {
				passes = passes && blockHandler.processBlock(block);
			}

			secondBranchTracker = secondBranchTracker.child;
			secondBranchCount++;
		}

		printPassFail(27, passes);
		return passes;
	}

	public static void main(String[] args) {
		Assignment3 runner = new Assignment3();
		int numSuccess = 0;
		int numTotal = 27;
		for(int i = 1; i <= numTotal; i++) {
			try {
				boolean success = (boolean)runner.getClass().getMethod("test" + Integer.toString(i)).invoke(runner);
				if(success) {
					numSuccess += 1;
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
		}	
		System.out.println("=============================================");
		System.out.println(Integer.toString(numSuccess) + "/" + Integer.toString(numTotal) + " tests passed");		

	}

}
