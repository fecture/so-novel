package com.pcdd.sonovel.parse;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.ChapterConverter;
import com.pcdd.sonovel.core.Source;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.ConfigBean;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.util.CrawlUtils;
import lombok.SneakyThrows;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

/**
 * @author pcdd
 * Created at 2024/3/27
 */
public class ChapterParser extends Source {

    private static final int TIMEOUT_MILLS = 15_000;
    private final ChapterConverter chapterConverter;

    public ChapterParser(ConfigBean config) {
        super(config);
        this.chapterConverter = new ChapterConverter(config);
    }

    @SneakyThrows
    public Chapter parse(Chapter chapter) {
        chapter.setContent(crawl(chapter.getUrl(), false));
        return chapter;
    }

    public Chapter parse(Chapter chapter, CountDownLatch latch, SearchResult sr) {
        try {
            Console.log("<== 正在下载: 【{}】", chapter.getTitle());
            // ExceptionUtils.randomThrow();
            chapter.setContent(crawl(chapter.getUrl(), false));
            latch.countDown();
            return chapterConverter.convert(chapter, config.getExtName());

        } catch (Exception e) {
            return retry(chapter, latch, sr);
        }
    }

    private Chapter retry(Chapter chapter, CountDownLatch latch, SearchResult sr) {
        for (int attempt = 1; attempt <= config.getMaxRetryAttempts(); attempt++) {
            try {
                Console.log("==> 章节下载失败，正在重试: 【{}】，尝试次数: {}/{}", chapter.getTitle(), attempt, config.getMaxRetryAttempts());
                chapter.setContent(crawl(chapter.getUrl(), true));
                Console.log("<== 重试成功: 【{}】", chapter.getTitle());
                latch.countDown();
                return chapterConverter.convert(chapter, config.getExtName());

            } catch (Exception e) {
                Console.error("==> 第 {} 次重试失败: 【{}】，原因: {}", attempt, chapter.getTitle(), e.getMessage());
                if (attempt == config.getMaxRetryAttempts()) {
                    latch.countDown();
                    // 最终失败时记录日志
                    saveErrorLog(chapter, sr, e.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * 爬取正文内容
     */
    private String crawl(String url, boolean isRetry) throws InterruptedException, IOException {
        boolean isPaging = this.rule.getChapter().isPagination();
        String nextUrl = url;
        StringBuilder sb = new StringBuilder();

        do {
            Document document = getConn(nextUrl, TIMEOUT_MILLS).get();
            Elements elContent = document.select(this.rule.getChapter().getContent());
            sb.append(elContent.html());
            // 章节不分页，只请求一次
            if (!isPaging) break;

            Elements elNextPage = document.select(this.rule.getChapter().getNextPage());
            // 章节最后一页 TODO 此处容易出错，先标记
            if (elNextPage.text().contains("下一章")) break;

            String href = elNextPage.attr("href");
            nextUrl = CrawlUtils.normalizeUrl(href, this.rule.getUrl());
            // 随机爬取间隔，建议重试间隔稍微长一点
            CrawlUtils.randomSleep(isRetry ? config.getRetryMinInterval() : config.getMinInterval(), isRetry ? config.getRetryMaxInterval() : config.getMaxInterval());
        } while (true);

        return sb.toString();
    }

    private void saveErrorLog(Chapter chapter, SearchResult sr, String errMsg) {
        String line = StrUtil.format("下载失败章节：【{}】({})，原因：{}", chapter.getTitle(), chapter.getUrl(), errMsg);
        String path = StrUtil.format("{}{}《{}》（{}）下载失败章节.log", config.getDownloadPath(), File.separator, sr.getBookName(), sr.getAuthor());

        try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
            // 自带换行符
            pw.println(line);

        } catch (IOException e) {
            Console.error(e);
        }
    }

}