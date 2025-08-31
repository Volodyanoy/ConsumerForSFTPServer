package org.example.volodyanoy;


import com.jcraft.jsch.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SFTPConsumer {
    private static final String PATH = "/upload/records.json";
    private static final Validator validator = new Validator();


    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.println("Добро пожаловать в SFTP-клиент. Для подключения к SFTP-серверу необходимы: IP-адрес, порт, логин, пароль");
        System.out.println("Введите IP-адрес");

        String ip = input.nextLine();

        System.out.println("Введите порт");
        int port = Integer.parseInt(input.nextLine());

        System.out.println("Введите логин");
        String login = input.nextLine();

        System.out.println("Введите пароль");
        String password = input.nextLine();

        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            session = createSession(ip, port, login, password);
            channelSftp = createSFTPChannel(session);
            boolean isWorking = true;
            Map<String, String> records = loadDNSRecords(channelSftp);

            while (isWorking) {
                showMenu();
                String choice = input.nextLine();
                switch (choice) {
                    case "1":
                        showDNSRecords(records);
                        break;
                    case "2":
                        getIpByDomain(records, input);
                        break;
                    case "3":
                        getDomainByIp(records, input);
                        break;
                    case "4":
                        addDNSRecord(channelSftp, records, input);
                        break;
                    case "5":
                        deleteDNSRecord(channelSftp, records, input);
                        break;
                    case "6":
                        isWorking = false;
                        break;
                    default:
                        System.out.println("Внимание! Выбор некорректный");
                }
            }

        } catch (Exception e) {
            System.out.println("Ошибка " + e.getMessage());
        } finally {
            if (channelSftp != null) channelSftp.exit();
            if (session != null) session.disconnect();
            input.close();
            System.out.println("Соединение закрыто");
        }

    }

    public static Session createSession(String ip, int port, String login, String password) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(login, ip, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no"); // отключаем проверку ключа хоста
        session.connect();

        return session;
    }

    public static ChannelSftp createSFTPChannel(Session session) throws JSchException {
        Channel channel = session.openChannel("sftp");
        channel.connect();

        return (ChannelSftp) channel;
    }

    public static void showMenu() {
        System.out.println();
        System.out.println("Меню возможных действий:");
        System.out.println("1. Получение списка пар \"домен – адрес\" из файла");
        System.out.println("2. Получение IP-адреса по доменному имени");
        System.out.println("3. Получение доменного имени по IP-адресу");
        System.out.println("4. Добавление новой пары \"домен – адрес\" в файл");
        System.out.println("5. Удаление пары \"домен – адрес\" по доменному имени или IP-адресу");
        System.out.println("6. Завершение работы");
        System.out.print("Выберите действие: ");
    }

    private static Map<String, String> loadDNSRecords(ChannelSftp channelSftp) {
        Map<String, String> map = new TreeMap<>();

        try (InputStream in = channelSftp.get(PATH); Scanner scanner = new Scanner(in, "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine());
            }
            String json = sb.toString();
            Pattern pattern = Pattern.compile(
                    "\"domain\"\\s*:\\s*\"([^\"]+)\".*?\"ip\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(json);


            while (matcher.find()) {
                String domain = matcher.group(1);
                String ip = matcher.group(2);
                map.put(domain.trim(), ip.trim());
            }

        } catch (Exception e) {

            System.out.println("Ошибка чтения " + e.getMessage());
        }
        return map;
    }

    private static void saveDNSRecords(ChannelSftp channelSftp, Map<String, String> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"addresses\": [\n");
        int size = records.size();
        int i = 0;
        for (Map.Entry<String, String> entry : records.entrySet()) {
            sb.append("    {\"domain\": \"")
                    .append(entry.getKey())
                    .append("\", \"ip\": \"")
                    .append(entry.getValue())
                    .append("\"}");
            if (i < size - 1) sb.append(",");
            sb.append("\n");
            i++;
        }
        sb.append("  ]\n}");
        try (ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8))) {
            channelSftp.put(in, PATH, ChannelSftp.OVERWRITE);
        } catch (Exception e) {
            System.out.println("Ошибка записи " + e.getMessage());
        }
    }

    private static void showDNSRecords(Map<String, String> records) {
        records.forEach((k, v) -> System.out.println(k + " " + v));
    }

    private static void getIpByDomain(Map<String, String> records, Scanner input) {
        System.out.println("Введите домен");
        String domain = input.nextLine().trim();
        if (!validator.isValidDomain(domain)) {
            System.out.println("Внимание! Некорректные входные данные");
            return;
        }
        String foundedIp = records.get(domain);
        if (foundedIp != null)
            System.out.println(foundedIp);
        else
            System.out.println("IP-адрес не найден");
    }

    private static void getDomainByIp(Map<String, String> records, Scanner input) {
        System.out.println("Введите IP-адрес");
        String ip = input.nextLine().trim();
        if (!validator.isValidIp(ip)) {
            System.out.println("Внимание! Некорректные входные данные");
            return;
        }
        String domain = records.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(ip))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (domain != null) {
            System.out.println(domain);
        } else {
            System.out.println("Домен не найден");
        }
    }

    private static void addDNSRecord(ChannelSftp channelSftp, Map<String, String> records, Scanner input) {
        System.out.println("Введите домен");
        String domain = input.nextLine();
        if (!validator.isValidDomain(domain)) {
            System.out.println("Внимание! Некорректные входные данные");
            return;
        }
        if (records.containsKey(domain)) {
            System.out.println("Внимание! Запись для указанного домена уже существует");
            return;
        }

        System.out.println("Введите IP-адрес");
        String ip = input.nextLine();
        if (!validator.isValidIp(ip)) {
            System.out.println("Внимание! Некорректные входные данные");
            return;
        }
        if (records.containsValue(ip)) {
            System.out.println("Внимание! Запись для указанного IP-адреса уже существует");
            return;
        }

        records.put(domain.trim(), ip.trim());
        saveDNSRecords(channelSftp, records);

    }

    private static void deleteDNSRecord(ChannelSftp channelSftp, Map<String, String> records, Scanner input) {
        System.out.println("Введите IP-адрес или домен");
        String s = input.nextLine().trim();
        boolean changed = false;

        if (validator.isValidIp(s)) {
            changed = records.entrySet().removeIf(entry -> entry.getValue().equals(s));
        } else if (validator.isValidDomain(s)) {
            changed = (records.remove(s) != null);
        } else {
            System.out.println("Внимание! Некорректные входные данные");
        }

        if (changed) {
            saveDNSRecords(channelSftp, records);
            System.out.println("Запись удалена");
        } else if (validator.isValidDomain(s) || validator.isValidIp(s)) {
            System.out.println("Запись не найдена");
        }

    }


}
