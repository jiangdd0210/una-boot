package com.ramostear.unaboot.web;

import com.ramostear.unaboot.domain.entity.*;
import com.ramostear.unaboot.domain.param.PostQuery;
import com.ramostear.unaboot.domain.vo.PostListVO;
import com.ramostear.unaboot.domain.vo.PostVO;
import com.ramostear.unaboot.service.CategoryService;
import com.ramostear.unaboot.service.PostCategoryService;
import com.ramostear.unaboot.service.PostService;
import com.ramostear.unaboot.service.TagService;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

/**
 * @ClassName FrontController
 * @Description TODO
 * @Author ramostear
 * @Date 2019/11/22 0022 3:28
 * @Version 1.0
 **/
@Slf4j
@Controller
public class FrontController extends UnaController{

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TagService tagService;

    @Autowired
    private PostService postService;

    @Autowired
    private PostCategoryService postCategoryService;

    private static CacheManager cacheManager = CacheManager.newInstance();

    /**
     * 网站主页
     * @return
     */
    @GetMapping(value = {"/","/index","/index.html","/home.html"})
    public String index(){
        return view("index.html");
    }

    /**
     * 栏目页面
     * @param slug      栏目访问名称
     * @param offset    是否存在数据分页，默认为第一页
     * @param model     回显数据模型
     * @return          栏目模板页面
     */
    @GetMapping("/{slug}")
    public String category(@PathVariable("slug")String slug,
                           @RequestParam(name = "offset",defaultValue = "1")Integer offset,
                           Model model){
        Category category = categoryService.getBySlug(slug);
        if(category != null){
            model.addAttribute("category",category)
                 .addAttribute("slug",slug)
                 .addAttribute("offset",offset);
            return view(category.getTemplate());
        }else{
            return view("index.html");
        }
    }

    /**
     * 访问标签页面，如果没有该标签，返回首页
     * @param slug      标签地址名称
     * @param offset    默认的文章分页起始位置
     * @param model     回显数据
     * @return          tags.html
     */
    @GetMapping("/tags/{slug}")
    public String tags(@PathVariable("slug")String slug,@RequestParam(name = "offset",defaultValue = "1")Integer offset, Model model){
        Tag tag = tagService.findBySlug(slug);
        if(tag != null){
            model.addAttribute("tag",tag)
                 .addAttribute("slug",slug)
                 .addAttribute("offset",offset);
            return view("tags.html");
        }else{
            return view("index.html");
        }
    }

    @GetMapping("/post/**")
    public String post(HttpServletRequest request,Model model){
        String uri = request.getRequestURI();
        if(StringUtils.isBlank(uri)){
            return redirect("/");
        }
        String slug = uri.substring(uri.indexOf("post/")+5);
        if(StringUtils.isBlank(slug)){
            return redirect("/");
        }
        log.info("post slug:[{}]",slug);
        Post post = postService.findBySlug(slug);
        if(post == null || post.getStatus() == 0){
            return redirect("/");
        }
        post.setVisits(visitsCache(post));
        PostVO postVO = postService.convertToPostVO(post);
        model.addAttribute("post",postVO);
        return view(post.getTemplate());
    }


    @GetMapping("/archives/{archive}")
    public String tag(@PathVariable("archive")String archive,Model model){
        if(StringUtils.isBlank(archive)){
            return redirect("/");
        }
        model.addAttribute("archive",archive);
        return view("archive.html");
    }

    @GetMapping("/guides/{slug}")
    public String guide(@PathVariable("slug")String slug){
        Category category = categoryService.getBySlug(slug);
        if(category != null){
            Post post = postCategoryService.findTopPostByCategoryId(category.getId());
            if(post != null){
                return redirect("/post/"+post.getSlug());
            }else{
                return redirect("/");
            }
        }else{
            return redirect("/");
        }
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "keyword",defaultValue = "",required = false)String keyword,
                         @RequestParam(name = "offset",defaultValue = "1")Integer offset,Model model){
        if(StringUtils.isBlank(keyword)){
            model.addAttribute("posts",null);
        }else{
            PostQuery postQuery = new PostQuery();
            postQuery.setKeyword(keyword);
            postQuery.setStatus(1);
            Page<Post> postPage = postService.search(postQuery, PageRequest.of(offset-1,15));
            if(CollectionUtils.isEmpty(postPage.getContent())){
                model.addAttribute("posts",null);
            }else{
                Page<PostListVO> postListVOS = postService.convertToPostListVO(postPage);
                model.addAttribute("posts",postListVOS);
            }
        }
        model.addAttribute("offset",offset);
        model.addAttribute("keyword",keyword);
        return view("search.html");
    }

    private String view(String template){
        Theme theme = (Theme) servletContext.getAttribute("theme");
        return "/themes/"+theme.getName()+"/"+template.substring(0,template.indexOf("."));
    }

    private Long visitsCache(Post post){
        Ehcache ehcache = cacheManager.getEhcache("dayHits");
        Element element = ehcache.get(post.getId()+"_visits");
        long count = 0;
        if(element != null){
            count = (Long) element.getObjectValue();
        }
        count++;
        ehcache.put(new Element(post.getId()+"_visits",count));
        if(count > 50){
            post.setVisits(post.getVisits()+count);
            postService.update(post);
            ehcache.remove(post.getId()+"_visits");
            return post.getVisits();
        }else{
            return post.getVisits();
        }

    }
}
