package dev.aaguirre.prueba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.aaguirre.prueba.ui.theme.PRUEBATheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PRUEBATheme {
                Login()
            }
        }
    }
}

@Composable
fun Login() {
    val auth = remember { FirebaseAuth.getInstance() }
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var conectado by remember {
        mutableStateOf(auth.currentUser != null)
    }
    var cargando by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (conectado) {
            Conversor()

            Button(onClick = {
                auth.signOut()
                conectado = false
                clave = ""
                mensaje = ""
            }) {
                Text("Cerrar sesión")
            }

        } else {
            Text("Iniciar sesión")

            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = !cargando,
                onClick = {
                    if (correo.isBlank() || clave.isBlank()) {
                        mensaje = "Completa ambos campos."
                    } else {
                        cargando = true
                        mensaje = ""

                        auth.signInWithEmailAndPassword(
                            correo.trim(), clave
                        ).addOnCompleteListener { tarea ->
                            cargando = false
                            conectado = tarea.isSuccessful
                            if (!tarea.isSuccessful) {
                                mensaje = "No se pudo ingresar. Revisa tus datos y conexión."
                            }
                        }
                    }
                }
            ) {
                Text(if (cargando) "Ingresando..." else "Ingresar")
            }

            Text(mensaje)
        }
    }
}

@Composable
fun Conversor() {
    val db = remember { FirebaseFirestore.getInstance() }
    var tasas by remember {
        mutableStateOf<Map<String, Double>>(emptyMap())
    }
    var monto by remember { mutableStateOf("") }
    var origen by remember { mutableStateOf("USD") }
    var destino by remember { mutableStateOf("PEN") }
    var resultado by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("rates").get()
            .addOnSuccessListener { documentos ->
                val leidas = documentos.documents.mapNotNull { documento ->
                    val tasa = (documento.get("Tasa") as? Number)?.toDouble()
                    if (tasa != null && tasa.isFinite() && tasa > 0) {
                        documento.id to tasa
                    } else {
                        null
                    }
                }.toMap()

                val monedas = listOf("USD", "EUR", "PEN", "GBP", "GPY")
                if (monedas.all { it in leidas }) {
                    tasas = leidas
                    error = ""
                } else {
                    error = "Revisa las cinco monedas y el campo Tasa."
                }
            }
            .addOnFailureListener {
                error = "Error al cargar monedas: ${it.message}"
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Conversor de monedas",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = monto,
            onValueChange = {
                monto = it
                resultado = ""
            },
            label = { Text("Monto") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )

        SelectorMoneda("Origen", origen, tasas.keys.sorted()) {
            origen = it
            resultado = ""
        }

        SelectorMoneda("Destino", destino, tasas.keys.sorted()) {
            destino = it
            resultado = ""
        }

        Button(
            enabled = tasas.isNotEmpty(),
            onClick = {
                val cantidad = monto.trim()
                    .replace(",", ".")
                    .toDoubleOrNull()

                if (cantidad == null || !cantidad.isFinite() || cantidad <= 0) {
                    error = "Ingresa un monto válido mayor que cero."
                    resultado = ""
                } else {
                    val valor = cantidad / tasas.getValue(origen) *
                            tasas.getValue(destino)

                    if (!valor.isFinite()) {
                        error = "El monto es demasiado grande."
                        resultado = ""
                    } else {
                        error = ""
                        resultado = String.format(
                            Locale.US,
                            "%.2f %s equivalen a %.2f %s",
                            cantidad, origen, valor, destino
                        )
                    }
                }
            }
        ) {
            Text("Convertir")
        }

        if (resultado.isNotEmpty()) {
            Text(resultado)
        }

        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        } else if (tasas.isEmpty()) {
            Text("Cargando monedas...")
        }
    }
}

@Composable
fun SelectorMoneda(
    etiqueta: String,
    seleccionada: String,
    monedas: List<String>,
    alSeleccionar: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Column {
        Text(etiqueta)

        Box {
            OutlinedButton(
                enabled = monedas.isNotEmpty(),
                onClick = { abierto = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$seleccionada ▼")
            }

            DropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false }
            ) {
                monedas.forEach { moneda ->
                    DropdownMenuItem(
                        text = { Text(moneda) },
                        onClick = {
                            alSeleccionar(moneda)
                            abierto = false
                        }
                    )
                }
            }
        }
    }
}