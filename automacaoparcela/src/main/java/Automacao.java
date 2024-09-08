import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.Comparator;

// abrir o WhatsApp e ler planilha xlsx
public class Automacao {
    private static WebDriver navegador;
    private static final ReentrantLock lock = new ReentrantLock();
    private static final SimpleDateFormat inputDateFormat = new SimpleDateFormat("dd/MM/yy");  // Formato de entrada da planilha
    private static final SimpleDateFormat outputDateFormat = new SimpleDateFormat("dd/MM/yyyy"); // Formato de saída desejado

    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\leona\\OneDrive\\Área de Trabalho\\AutomacaoParcela\\automacaoparcela\\bin\\chromedriver\\chromedriver.exe");
        navegador = new ChromeDriver();

        navegador.get("https://web.whatsapp.com/");
        try {
            TimeUnit.SECONDS.sleep(35);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        InputStream inputStream = null;
        List<String[]> linhas = null;
        try {
            inputStream = new FileInputStream(
                    "C:\\Users\\leona\\OneDrive\\Área de Trabalho\\AutomacaoParcela\\automacaoparcela\\bin\\main\\resources\\Cobranca\\cobranca.xlsx");
            linhas = planilha(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (linhas != null) {
            ExecutorService exec = Executors.newFixedThreadPool(15);

            for (String[] linha : linhas) {
                exec.submit(() -> enviarMensagem(linha));
            }
            exec.shutdown();
            try {
                exec.awaitTermination(15, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        navegador.quit();
    }

    // ler as linhas da planilha xlsx
    public static List<String[]> planilha(InputStream inputStream) {
        List<String[]> linhas = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true;
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                String Cliente = getCellValue(row.getCell(0));
                String Vencimento = getCellValue(row.getCell(1));
                String Pagamento = getCellValue(row.getCell(2));
                String Seguradora = getCellValue(row.getCell(3));
                String Telefone = getCellValue(row.getCell(4));
                String Consultor = getCellValue(row.getCell(5));

                // quando tiver linha vazia, pular para a próxima
                if (Cliente.isEmpty() || Telefone.isEmpty() || Vencimento.isEmpty() || Pagamento.isEmpty() || Seguradora.isEmpty() || Consultor.isEmpty()) {
                    continue;
                }

                // tratar o erro do número de telefone para o link do WhatsApp reconhecer
                String TelefoneLimpo = Telefone.replaceAll("[^0-9]", "");

                // formatar a data de vencimento
                String VencimentoFormatado = formatarData(Vencimento);

                String[] linha = new String[6];
                linha[0] = Cliente;
                linha[1] = VencimentoFormatado;
                linha[2] = Pagamento;
                linha[3] = Seguradora;
                linha[4] = TelefoneLimpo;
                linha[5] = Consultor;

                linhas.add(linha);
            }

            // coloar em ordem as linhas da planilha por data de vencimento
            linhas.sort(Comparator.comparing(o -> o[1]));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return linhas;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return outputDateFormat.format(cell.getDateCellValue());
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    // formatar a data de vencimento
    private static String formatarData(String data) {
        try {
            Date date = inputDateFormat.parse(data);
            return outputDateFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return data; 
            // retorna a data original em caso de erro
        }
    }

    // enviar a mensagem
    public static void enviarMensagem(String[] linha) {
        lock.lock();
        try {
            String Cliente = linha[0];
            String Vencimento = linha[1];
            String Pagamento = linha[2];
            String Seguradora = linha[3];
            String TelefoneStr = linha[4];
            String Consultor = linha[5];

            String TelefoneFormatado = TelefoneStr.replaceAll("[^0-9]", "");

            if (TelefoneFormatado.length() != 11) {
                System.out.println("Número de telefone inválido, favor corrigir: " + TelefoneStr);
                escreverErro(Cliente, TelefoneStr, Consultor, "Número de telefone inválido");
                return;
            }

            String mensagem = String.format(
                    "Olá %s, venho através desta mensagem lembrar que a parcela de seu seguro %s vencerá em %s através da forma de pagamento: %s. Caso tenha alguma dúvida, entre em contato com o(a) especialista de seguros que lhe atendeu: %s.",
                    Cliente, Seguradora, Vencimento, Pagamento, Consultor);

            String linkWhatsApp = "https://web.whatsapp.com/send?phone=55" + TelefoneFormatado + "&text="
                    + URLEncoder.encode(mensagem, StandardCharsets.UTF_8);
            navegador.get(linkWhatsApp);
            TimeUnit.SECONDS.sleep(20);

            navegador.findElement(By.xpath("//*[@id='main']/footer/div[1]/div/span[2]/div/div[2]/div[2]/button/span"))
                    .click();
            TimeUnit.SECONDS.sleep(5);

        } catch (Exception e) {
            System.out.println("Não foi possível enviar a mensagem para " + linha[0] + " com o telefone " + linha[4]);
            escreverErro(linha[0], linha[4], linha[5], e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public static void escreverErro(String Cliente, String Telefone, String Consultor, String motivo) {
        try (FileWriter escrever = new FileWriter("erros.csv", true)) {
            escrever.write(Cliente + "," + Telefone + "," + Consultor + "," + motivo + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
