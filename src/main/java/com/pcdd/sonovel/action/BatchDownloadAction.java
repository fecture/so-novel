package com.pcdd.sonovel.action;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.parse.SearchParser;
import com.pcdd.sonovel.parse.TocParser;
import lombok.AllArgsConstructor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.fusesource.jansi.AnsiRenderer.render;

/**
 * @author pcdd
 * Created at 2025/2/21
 */
@AllArgsConstructor
public class BatchDownloadAction {

    private final AppConfig config;
    public static final String DIVIDER = "=".repeat(50);

    public void execute() {
        Scanner sc = Console.scanner();
        List<String> lines = new ArrayList<>();
        Console.log(render("==> 请依次输入书名和作者，用空格隔开，每输入一条按回车。输入 # 结束: ", "green"));
        while (true) {
            String line = sc.nextLine();
            if ("#".equals(line.strip())) {
                break;
            }
            if (StrUtil.isNotEmpty(line)) {
                lines.add(line);
            }
        }

        SearchParser sp = new SearchParser(config);
        List<SearchResult> downloadList = new ArrayList<>();
        StringBuilder notFound = new StringBuilder();

        for (String line : lines) {
            String[] split = line.split("\\s+");
            String bookName = split[0];
            String author = split[1];
            List<SearchResult> res = sp.parse(bookName, true);
            res.stream()
                    .filter(sr -> bookName.equals(sr.getBookName()) && author.equals(sr.getAuthor()))
                    .findFirst()
                    .ifPresentOrElse(sr -> {
                        Console.log("<== 已找到：《{}》({}) {}", sr.getBookName(), sr.getAuthor(), sr.getUrl());
                        downloadList.add(sr);
                    }, () -> {
                        Console.log("<== 未找到：《{}》({})", bookName, author);
                        notFound.append(bookName).append(" ").append(author).append("\n");
                    });
        }

        Console.log("<== 共计 {} 本，已找到 {} 本，未找到 {} 本", lines.size(), downloadList.size(), lines.size() - downloadList.size());
        // 一本书也没有搜到
        if (downloadList.isEmpty()) {
            return;
        }
        // 存在未搜到的书
        if (!notFound.isEmpty()) {
            notFound.append("#\n");
            notFound.append("若要继续下载上述未搜到的书: 切换其它书源，复制以上内容，粘贴到批量下载，以此类推……\n");
            FileUtil.writeUtf8String(notFound.toString(),
                    System.getProperty("user.dir") + File.separator + "批量下载 - 书源 %s 未搜到的书.log".formatted(config.getSourceId()));
        }
        Console.print(render("==> 输入 Y 以确认下载：", "green"));
        if ("Y".equalsIgnoreCase(sc.next().strip())) {
            double totalTime = 0;
            TocParser tocParser = new TocParser(config);

            for (SearchResult sr : downloadList) {
                Console.log("\n{} START 《{}》({}) {}", DIVIDER, sr.getBookName(), sr.getAuthor(), DIVIDER);
                List<Chapter> toc = tocParser.parse(sr.getUrl(), 1, Integer.MAX_VALUE);
                double res = new Crawler(config).crawl(sr, toc);
                Console.log("<== 完成！总耗时 {} s\n", NumberUtil.round(res, 2));
                Console.log("{} END 《{}》({}) {}\n", DIVIDER, sr.getBookName(), sr.getAuthor(), DIVIDER);
                totalTime += res;
            }

            Console.log("<== 批量下载完成！总耗时 {}\n", DateUtil.formatBetween(Convert.toLong(totalTime * 1000)));
        }
    }

}