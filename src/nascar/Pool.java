package nascar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Pool {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println(
                    "Missing Arguments. Expecting: Pool <resultsFile> <picksFile> <totalsFile> [<qualifyingCanceled y|n>]");
        }

        try {
            String qualifyingCanceledArg;
            if (args.length == 4) {
                qualifyingCanceledArg = args[3];
            } else {
                Scanner reader = new Scanner(System.in);
                System.out.println("Was Qualifying canceled? <y|n>: ");
                qualifyingCanceledArg = reader.next();
                reader.close();
            }
            boolean qualifyingCanceled = qualifyingCanceledArg.toLowerCase().startsWith("y") ? true : false;
            System.out.println("Calculating results. Qualifying canceled=" + qualifyingCanceled);

            Results results = new Results(args[0]);
            System.out.println(results);
            Players players = new Players(args[1]);
            Totals totals = new Totals(args[2]);

            players.applyTotals(totals);
            System.out.println("\nPicks and current Totals:");
            players.getPlayers().stream()
                    .sorted()
                    .forEach(p -> System.out.println(p));

            players.applyResults(results, qualifyingCanceled);
            System.out.println("\nPoints and updated totals:");
            players.getPlayers().stream()
                    .sorted()
                    .forEach(p -> System.out.println(p));

            System.out.println("\nResults E-Mail:");
            List<Player> sortedPlayers = players.getPlayers().stream()
                    .sorted()
                    .collect(Collectors.toList());
            Player player4 = sortedPlayers.get(3);
            for (int i = 4; (i < sortedPlayers.size()); ++i) {
                if (player4.getPoints() == sortedPlayers.get(i).getPoints()) {
                    player4 = sortedPlayers.get(i);
                }
            }
            String s = String.format("#%d, %s with %d takes..........%d", 4, player4.getName(), player4.getPoints(),
                    results.getHighestCar(player4.getPicks()));
            System.out.println(s);
            for (int i = (sortedPlayers.size() - 1); i >= 0; --i) {
                Player p = sortedPlayers.get(i);
                if (p.equals(player4)) {
                    continue;
                }
                s = String.format("#%d, %s with %d takes..........%d", i + 1, p.getName(), p.getPoints(),
                        results.getHighestCar(p.getPicks()));
                System.out.println(s);
            }

            System.out.println("\nYTD Standings (by total points):\n");
            sortedPlayers = players.getPlayers().stream()
                    .sorted(new Comparator<Player>() {
                        public int compare(Player o1, Player o2) {
                            return Integer.compare(o2.getTotal(), o1.getTotal());
                        }
                    })
                    .collect(Collectors.toList());

            for (int i = 0; i < sortedPlayers.size(); ++i) {
                Player p = sortedPlayers.get(i);
                s = String.format("%s.........%d.....%s", p.getName(), p.getTotal(), p.getBalanceString());
                System.out.println(s);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to parse pool files: " + e.getMessage());
        }
    }

    private static class Results {
        File file;
        private Map<Integer, Result> results = new HashMap<>();

        Results(String results) {
            this.file = new File(results);
            process();
        }

        private void process() {
            if (!file.canRead()) {
                throw new IllegalArgumentException("Can't read results file: " + file.getAbsolutePath());
            }

            String regex = "\\s*(\\d+)\\s+(\\d+)\\s+(\\d+).*?(\\d+)\\s+\\b(Running|Accident|Engine)\\b.*";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher("");

            //read file into stream, try-with-resources
            try (Stream<String> stream = Files.lines(Paths.get(file.toURI()))) {

                stream.forEach(l -> {
                    if (!l.trim().isEmpty()) {
                        matcher.reset(l);
                        if (!matcher.matches() || matcher.groupCount() != 5) {
                            throw new IllegalArgumentException("Invalid line in results file: " + l);
                        }
                        Result r = new Result(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
                        results.put(r.getCarNumber(), r);
                        //System.out.println(r);
                    }
                });

            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public Map<Integer, Result> getResults() {
            return results;
        }

        public int getHighestCar(List<Integer> cars) {
            Integer highest = cars.get(0);
            for (Integer car : cars) {
                Result r = results.get(car);
                if (null == r) {
                    continue;
                }
                if (r.getPoints() > results.get(highest).getPoints()) {
                    highest = car;
                }
            }
            return highest;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer("Results:");
            results.values().stream()
                    .sorted()
                    .forEach(r -> sb.append(r.toString()));
            return sb.toString();
        }
    }

    private static class Result implements Comparable<Result> {
        int carNumber;
        int start;
        int finish;
        int points;

        public Result(String finish, String start, String carNumber, String points) {
            super();
            this.finish = Integer.valueOf(finish);
            this.start = Integer.valueOf(start);
            this.carNumber = Integer.valueOf(carNumber);
            this.points = Integer.valueOf(points);
        }

        public int getCarNumber() {
            return carNumber;
        }

        public int getFinish() {
            return finish;
        }

        public int getPoints() {
            return points;
        }

        public int getStart() {
            return start;
        }

        @Override
        public String toString() {
            return "\nResult [finish=" + finish + ", carNumber=" + carNumber + ", points=" + points + ", start=" + start 
                    + "]";
        }

        @Override
        public int compareTo(Result o) {
            return Integer.compare(finish, o.finish);
        }
    }

    private static class Players {
        File file;
        private List<Player> players = new ArrayList<>();

        Players(String picks) {
            this.file = new File(picks);
            process();
        }

        private void process() {
            if (!file.canRead()) {
                throw new IllegalArgumentException("Can't read picks file: " + file.getAbsolutePath());
            }

            String regex = "([a-zA-Z ]+)\\..*?(\\d+).*?(\\d+).*?(\\d+).*?(\\d+).*";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher("");

            //read file into stream, try-with-resources
            try (Stream<String> stream = Files.lines(Paths.get(file.toURI()))) {

                stream.forEach(l -> {
                    if (!l.trim().isEmpty()) {
                        matcher.reset(l);
                        if (!matcher.matches() || matcher.groupCount() != 5) {
                            throw new IllegalArgumentException("Invalid line in picks file: " + l);
                        }
                        Player p = new Player(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4),
                                matcher.group(5));
                        players.add(p);
                        //System.out.println(p);
                    }
                });

            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public List<Player> getPlayers() {
            return players;
        }

        public void applyResults(Results results, boolean qualifyingCanceled) {
            System.out.println("\nApplying Results...");
            players.stream()
                    .forEach(p -> {
                        p.picks.stream()
                                .forEach(c -> {
                                    Result r = results.getResults().get(c);
                                    if (null == r) {
                                        System.out.println("Player [" + p.getName() + "] Car [" + c
                                                + "] missing, may not have qualified!");
                                    } else {
                                        int points = r.getPoints();
                                        int bonus = !qualifyingCanceled && (r.getStart() < 3) ? 1 : 0;
                                        if (bonus == 1) {
                                            System.out.println("Player [" + p.getName()
                                                    + "] received a qualifying bonus point for car [" + c + "]!");
                                        }
                                        System.out.println(
                                                "Player [" + p.getName() + "] car=" + c + ", points=" + points);
                                        p.setPoints(p.getPoints() + points + bonus);
                                    }
                                });
                        p.setTotal(p.getTotal() + p.getPoints());
                    });

            List<Player> sortedPlayers = players.stream().sorted().collect(Collectors.toList());
            sortedPlayers.get(0).setBalance(sortedPlayers.get(0).getBalance() + 20);
            sortedPlayers.get(1).setBalance(sortedPlayers.get(1).getBalance() + 10);
            sortedPlayers.get(2).setBalance(sortedPlayers.get(2).getBalance() + 5);
            for (Player p : sortedPlayers.subList(3, sortedPlayers.size())) {
                p.setBalance(p.getBalance() - 5);
            }
        }

        public void applyTotals(Totals totals) {
            players.stream()
                    .forEach(p -> {
                        p.setBalance(totals.getTotals().get(p.getName()).getBalance());
                        p.setTotal(totals.getTotals().get(p.getName()).getTotal());
                    });
        }

        @Override
        public String toString() {
            return "Players [players=" + players + "]";
        }
    }

    private static class Player implements Comparable<Player> {
        private String name;
        private List<Integer> picks;
        private int points;
        private int total;
        private int balance;

        public Player(String player, String car1, String car2, String car3, String car4) {
            super();
            this.name = player.trim();
            this.picks = Arrays.asList(Integer.valueOf(car1), Integer.valueOf(car2), Integer.valueOf(car3),
                    Integer.valueOf(car4));
        }

        public String getName() {
            return name;
        }

        public List<Integer> getPicks() {
            return picks;
        }

        public int getPoints() {
            return points;
        }

        public void setPoints(int points) {
            this.points = points;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getBalance() {
            return balance;
        }

        public String getBalanceString() {
            if (0 == balance) {
                return "Even";
            }

            if (balance < 0) {
                return "-$" + (balance * -1);
            }

            return "+$" + balance;
        }

        public void setBalance(int balance) {
            this.balance = balance;
        }

        @Override
        public String toString() {
            return "Player [name=" + name + ", picks=" + picks + ", points=" + points + ", total=" + total
                    + ", balance=" + balance + "]";
        }

        @Override
        public int compareTo(Player o) {
            int res = Integer.compare(o.getPoints(), this.points);
            return res != 0 ? res : Integer.compare(o.getTotal(), this.total);
        }

    }

    private static class Totals {
        File file;
        private Map<String, Total> totals = new HashMap<>();

        Totals(String totals) {
            this.file = new File(totals);
            process();
        }

        private void process() {
            if (!file.canRead()) {
                throw new IllegalArgumentException("Can't read totals file: " + file.getAbsolutePath());
            }

            String regex = "([a-zA-Z ]+)\\..*?(\\d+).*?([-+$Even\\d]+).*";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher("");

            //read file into stream, try-with-resources
            try (Stream<String> stream = Files.lines(Paths.get(file.toURI()))) {

                stream.forEach(l -> {
                    if (!l.trim().isEmpty()) {
                        matcher.reset(l);
                        if (!matcher.matches() || matcher.groupCount() != 3) {
                            throw new IllegalArgumentException("Invalid line in totals file: " + l);
                        }
                        Total t = new Total(matcher.group(1), matcher.group(2), matcher.group(3));
                        totals.put(t.getPlayer(), t);
                        //System.out.println(t);
                    }
                });

            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public Map<String, Total> getTotals() {
            return totals;
        }

        @Override
        public String toString() {
            return "Totals [totals=" + totals + "]";
        }
    }

    private static class Total {
        String player;
        int total;
        int balance;

        public Total(String player, String points, String balance) {
            super();
            this.player = player.trim();
            this.total = Integer.valueOf(points);
            if ("even".equals(balance.toLowerCase())) {
                this.balance = 0;
            } else {
                this.balance = Integer.valueOf(balance.substring(2));
                if (balance.startsWith("-")) {
                    this.balance *= -1;
                }
            }
        }

        public String getPlayer() {
            return player;
        }

        public int getTotal() {
            return total;
        }

        public int getBalance() {
            return balance;
        }

        @Override
        public String toString() {
            return "Total [player=" + player + ", total=" + total + ", balance=" + balance + "]";
        }
    }

}
