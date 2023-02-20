package com.example.androidstarknetdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.androidstarknetdemo.ui.theme.AndroidStarknetDemoTheme
import com.swmansion.starknet.account.StandardAccount
import com.swmansion.starknet.data.types.Call
import com.swmansion.starknet.data.types.Felt
import com.swmansion.starknet.data.types.InvokeFunctionResponse
import com.swmansion.starknet.data.types.Uint256
import com.swmansion.starknet.data.types.transactions.TransactionReceipt
import com.swmansion.starknet.provider.exceptions.RequestFailedException
import com.swmansion.starknet.provider.gateway.GatewayProvider
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.math.BigInteger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidStarknetDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Main()
                }
            }
        }
    }
}

@Composable
fun Main() {
    // Create a variable that will store the address of our account
    // Replace "0x1234" with your own address
    // By default, it can be found in `~/.starknet_accounts/starknet_open_zeppelin_accounts.json`
    val accountAddress = Felt.fromHex("0x1234")

    // Create a coroutine scope
    val scope = rememberCoroutineScope()

    var balance by remember { mutableStateOf(Uint256.ZERO) }
    var transactionHash by remember { mutableStateOf(Felt.ZERO) }

    var receipt by remember { mutableStateOf<TransactionReceipt?>(null) }

    var recipientAddress by remember { mutableStateOf("") }
    var isErrorAddress by remember { mutableStateOf(true) }

    var amount by remember { mutableStateOf("") }
    var isErrorAmount by remember { mutableStateOf(true) }

    fun validateAddress(address: String) {
        isErrorAddress = try {
            Felt.fromHex(address)
            false
        } catch (_: Exception) {
            true
        }
    }

    fun validateAmount(amount: String) {
        isErrorAmount = try {
            Uint256(BigInteger(amount))
            false
        } catch (_: Exception) {
            true
        }
    }


    val queryStarknetOnClick: () -> Unit = {
        scope.launch {
            balance = checkBalance(accountAddress)
        }
    }

    val sendTransactionOnClick: () -> Unit = {
        scope.launch {
            transactionHash = transferToken(
                recipientAddress = Felt.fromHex(recipientAddress),
                amount = Uint256(BigInteger(amount)),
            ).transactionHash
        }
    }

    val statusOnClick: () -> Unit = {
        scope.launch {
            receipt = checkInvokeTransaction(transactionHash)
        }
    }


    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Current balance: ${balance.value} wei")
        Button(onClick = queryStarknetOnClick) {
            Text(text = "Check Balance")
        }
        if (transactionHash != Felt.ZERO) {
            Text(text = "Transaction hash: ${transactionHash.hexString()}")
        }

        ValidatedTextField(
            value = recipientAddress,
            isError = isErrorAddress,
            labelText = "Recipient address",
            errorText = "Invalid address",
            onValueChange = {
                validateAddress(it)
                recipientAddress = it
            }
        )

        ValidatedTextField(
            value = amount,
            isError = isErrorAmount,
            labelText = "Amount",
            errorText = "Invalid amount",
            onValueChange = {
                validateAmount(it)
                amount = it
            }
        )

        Button(onClick = sendTransactionOnClick, enabled = !isErrorAddress && !isErrorAmount) {
            Text(text = "Send Transaction")
        }

        TransactionStatus(receipt = receipt, onClick = statusOnClick)
    }
}

// Text validation based on https://stackoverflow.com/a/68575244
@Composable
fun ValidatedTextField(
    value: String,
    isError: Boolean,
    labelText: String,
    errorText: String,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = labelText) },
        singleLine = true,
        isError = isError,
    )
    if (isError) {
        Text(
            text = errorText,
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun TransactionStatus(receipt: TransactionReceipt?, onClick: () -> Unit) {
    Column {
        if (receipt != null) {
            Column {
                Text(text = "Transaction Receipt", style = MaterialTheme.typography.h5)
                Text(text = "Status: ${receipt.status}")
                Text(text = "Fee paid: ${receipt.actualFee?.decString()} wei")
            }
        }

        Button(onClick = onClick) {
            Text(text = "Check Transaction Status")
        }
    }
}

suspend fun checkBalance(accountAddress: Felt): Uint256 {
    // Create a testnet provider
    // Testnet is a separate Starknet test network operating alongside Starknet Mainnet
    val provider = GatewayProvider.makeTestnetProvider()

    // Create a call to Starknet ERC-20 ETH contract
    val call = Call(
        // `contractAddress` in this case is an address of ERC-20 ETH contract on Starknet testnet
        contractAddress = Felt.fromHex("0x049d36570d4e46f48e99674bd3fcc84644ddd6b96f7c741b1562b82f9e004dc7"),

        // `entrypoint` can be passed both as a string name and Felt value
        entrypoint = "balanceOf",

        // `calldata` is always required to be `List<Felt>`, so we wrap accountAddress in `listOf()`
        calldata = listOf(accountAddress)
    )

    // Create a Request object which has to be executed in synchronous
    // or asynchronous way
    val request = provider.callContract(call)

    // Execute a Request. This operation returns JVM CompletableFuture
    val future = request.sendAsync()

    // Await the completion of the future without blocking the main thread
    // this comes from `kotlinx-coroutines-jdk8`
    val balance: List<Felt> = future.await()

    // balanceOf returns the result as Uint256 which in Starknet is encoded as two Felts:
    // high and low. We can convert it to more readable value using starknet-jvm
    val (low, high) = balance
    return Uint256(low = low, high = high)
}

suspend fun transferToken(
    recipientAddress: Felt,
    amount: Uint256
): InvokeFunctionResponse {
    val provider = GatewayProvider.makeTestnetProvider()

    // Create an account instance
    // Replace placeholder values with your account details
    val address = Felt.fromHex("0x1234")
    val privateKey = Felt.fromHex("0x1111")
    val account = StandardAccount(address, privateKey, provider)

    // Create a call to ERC-20 contract that will be sent in transaction
    val call = Call(
        Felt.fromHex("0x049d36570d4e46f48e99674bd3fcc84644ddd6b96f7c741b1562b82f9e004dc7"),
        "transfer",
        listOf(recipientAddress) + amount.toCalldata()  // Uint256 can be converted to calldata (list of Felts) easily
    )
    // Create a Request that will send a transaction
    val transaction = account.execute(call)

    // Send a transaction
    return transaction.sendAsync().await()
}

suspend fun checkInvokeTransaction(transactionHash: Felt): TransactionReceipt? {
    val provider = GatewayProvider.makeTestnetProvider()

    return try {
        // Create an receipt Request
        val request = provider.getTransactionReceipt(transactionHash)
        request.sendAsync().await()
    } catch (e: RequestFailedException) {
        // We need to catch en exception here, because getTransactionReceipt
        // throws on unknown transactions.
        null
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AndroidStarknetDemoTheme {
        Main()
    }
}