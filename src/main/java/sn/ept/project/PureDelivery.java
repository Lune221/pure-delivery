package sn.ept.project;

import com.wavesplatform.wavesj.*;
import java.io.IOException;
import java.net.URISyntaxException;


public class PureDelivery {
    public static void main(String[] args) throws IOException, URISyntaxException {

        //fees
        final long FEE = 500000;
        final long DELIVERY_FEE = 200000;
        final long PRODUCT_PRICE = 100000000;
        final long SCRIPT_FEE = 100000;

        // Set testnet node
        Node node = new Node("https://testnode2.wavesnodes.com");

        //Set seeds for 2 accounts
        String SellerSeed = "bird away parade win token urge enact advance debris couple miracle captain cliff dismiss dress";

        String deliverySeed = "wrestle knock plastic way release knife zero attitude volume slam north tent bacon exit dish";

        //Get Private keys
        PrivateKeyAccount buyer = PrivateKeyAccount.fromPrivateKey("CKxd8CmhuyygerfufWUnj2py4gTz9iWpkaA3yGKmtSPc", Account.TESTNET);
        PrivateKeyAccount seller = PrivateKeyAccount.fromSeed(SellerSeed,0, Account.TESTNET);
        PrivateKeyAccount deliver = PrivateKeyAccount.fromSeed(deliverySeed,0, Account.TESTNET);

        //Generating random account
        String newAccountSeed = PrivateKeyAccount.generateSeed();
        PrivateKeyAccount intermediateAccount = PrivateKeyAccount.fromSeed(newAccountSeed, 0, Account.TESTNET);
        String intermediateAccountAddress = intermediateAccount.getAddress();
        System.out.print("Intermediate account Address: "  + intermediateAccountAddress + "" + "\n");

        // Here we suppose that the buyer wants to buy the product. So he sends the PRICE to the intermediate account
        Transaction buyer_to_random_account = Transaction.makeTransferTx(buyer, intermediateAccountAddress, PRODUCT_PRICE + SCRIPT_FEE + DELIVERY_FEE + 2 * FEE,"WAVES", FEE ,"WAVES", "Buyer Sending Product Price to Random Account");
        // Here we suppose that the delivery man wants to take the product. So he also sends the product price to the intermediate account
        Transaction delivery_to_random_account = Transaction.makeTransferTx(deliver, intermediateAccountAddress, PRODUCT_PRICE, "WAVES", FEE, "WAVES", "Delivery sending Product Price to Random Account");
        node.send(buyer_to_random_account);
        node.send(delivery_to_random_account);
        try {
            // Waiting 10 seconds to make sure the transactions are validated
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("buyerPK: " + Base58.encode(buyer.getPublicKey()));
        System.out.println("sellerPK: " + Base58.encode(seller.getPublicKey()));
        System.out.println("intermediatePK: " + Base58.encode(intermediateAccount.getPublicKey()));

        // Set Smart account Script and compile it
        String script = "let buyerPubKey  = base58'" + Base58.encode(buyer.getPublicKey()) + "';" +
                "let sellerPubKey = base58'" + Base58.encode(seller.getPublicKey()) + "';" +
                "let deliveryPubKey = base58'" + Base58.encode(deliver.getPublicKey()) + "';" +
                "let buyerSigned = if(sigVerify(tx.bodyBytes, tx.proofs[0], buyerPubKey)) then 1 else 0;" +
                "let sellerSigned = if(sigVerify(tx.bodyBytes, tx.proofs[1], sellerPubKey)) then 1 else 0;" +
                "let deliverySigned = if(sigVerify(tx.bodyBytes, tx.proofs[2], deliveryPubKey)) then 1 else 0;" +
                "buyerSigned + sellerSigned + deliverySigned >= 2";
        String bytecode = node.compileScript(script);
        System.out.println(bytecode);

        //intermediateAccount Make Script transaction and send it to the network
        try {
            Transaction stx = Transaction.makeScriptTx(intermediateAccount,bytecode, Account.TESTNET, SCRIPT_FEE);
            node.send(stx);
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Buyer make a transfer via the intermediate account
        Transaction intermediate_to_seller = Transaction.makeTransferTx(intermediateAccount, seller.getAddress(), PRODUCT_PRICE,"WAVES", FEE,"WAVES", "Sending currency to seller account");
        //Buyer and Delivery Man sign the deal with proofs
        intermediate_to_seller = PureDelivery.sign_by_2(intermediate_to_seller, buyer, 0, deliver, 2);
        // The delivery man get his money back
        Transaction intermediate_to_delivery = Transaction.makeTransferTx(intermediateAccount, deliver.getAddress(), PRODUCT_PRICE + DELIVERY_FEE, "WAVES", FEE, "WAVES", "Sending currency to delivery account");
        intermediate_to_delivery = PureDelivery.sign_by_2(intermediate_to_delivery, buyer, 0, seller, 1);
        // Send the transfer transactions ot the network
        String txid = node.send(intermediate_to_seller);
        txid += " - " ;
        txid += node.send(intermediate_to_delivery);
        System.out.println(txid);
    }
    
    private static Transaction sign_by_2 (Transaction transaction, PrivateKeyAccount acc1, int pos1, PrivateKeyAccount acc2, int pos2) {
        String sig1 =  acc1.sign(transaction);
        String sig2 = acc2.sign(transaction);
        transaction = transaction.withProof(pos1,sig1);
        transaction = transaction.withProof(pos2, sig2);

        return transaction;
    }
}