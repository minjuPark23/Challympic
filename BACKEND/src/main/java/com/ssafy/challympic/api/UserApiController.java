package com.ssafy.challympic.api;

import com.ssafy.challympic.domain.*;
import com.ssafy.challympic.service.InterestService;
import com.ssafy.challympic.service.MediaService;
import com.ssafy.challympic.service.TitleService;
import com.ssafy.challympic.service.UserService;
import com.ssafy.challympic.util.MD5Generator;
import com.ssafy.challympic.util.S3Uploader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;
    private final MediaService mediaService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final S3Uploader s3Uploader;
    private final TitleService titleService;
    private final InterestService interestService;

    @GetMapping("/user/account/{userNo}")
    public Result findUser(@PathVariable("userNo") int user_no){
        User findUser = userService.findUser(user_no);
        List<Title> titles = titleService.findTitlesByUserNo(user_no);
        List<titleDto> userTitles = new ArrayList<>();
        if(!titles.isEmpty()){
            userTitles = titles.stream()
                    .map(t -> new titleDto(t))
                    .collect(Collectors.toList());
        }

        List<Interest> interests = findUser.getInterest();
        List<interestDto> userInterests = new ArrayList<>();
        if(!interests.isEmpty()){
            userInterests = interests.stream()
                    .map(i -> new interestDto(i))
                    .collect(Collectors.toList());
        }

        if(findUser != null) {
            return new Result(true, HttpStatus.OK.value(), new UserDto(findUser, userTitles, userInterests));
        }else{
            return new Result(false, HttpStatus.BAD_REQUEST.value(), new UserDto());
        }
    }

    @PostMapping("/join")
    public Result join(@RequestBody joinUserRequest request){
        System.out.println("test");
        User newUser = new User();

        newUser.setUser_email(request.getUser_email());
        newUser.setUser_nickname(request.getUser_nickname());
        String rawPwd = request.getUser_pwd();
        String encPwd = bCryptPasswordEncoder.encode(rawPwd);
        newUser.setUser_pwd(encPwd);

        int join_no = userService.join(newUser);
        if(join_no > 0){
            return new Result(true, HttpStatus.OK.value());
        }else{
            return new Result(true, HttpStatus.BAD_REQUEST.value());
        }
    }

    /**
     * 파일 수정 추가 필요
     */
    @PutMapping("/user/account/{userNo}")
    public Result updateUser(@PathVariable("userNo") int user_no, updateUserRequest request){

        // 1. 플로우 시작
        Media media = null;
        MultipartFile files = null;

        try {
            if(request.getFile() != null){
                files = request.getFile();

                // 확장자 체크
                String fileType = getFileType(files);

                if(fileType == null)
                // 지원하지 않는 확장자
                {
                    return new Result(false, HttpStatus.OK.value());
                }

//            png/jpg, mp4 <- 확장자
//            media = s3Uploader.upload(files, 'image', 'profile');
                media = s3Uploader.upload(files, "image", "profile");

                if(media == null){
                    // AWS S3 업로드 실패
                    return new Result(false, HttpStatus.OK.value());
                }

                int file_no = mediaService.saveMedia(media);

                Media file = mediaService.getMedia(file_no);
                userService.updateUser(user_no, request.getUser_nickname(), file, request.getUser_title());
            }else{
                userService.updateUser(user_no, request.getUser_nickname(), null, request.getUser_title());
            }
            User user = userService.findUser(user_no);
            if(user != null) {
                return new Result(true, HttpStatus.OK.value(), new UserDto(user));
            }else{
                return new Result(false, HttpStatus.BAD_REQUEST.value(), new UserDto());
            }

        } catch(Exception e){
            e.printStackTrace();
        }

        return new Result(false, HttpStatus.BAD_REQUEST.value());
    }

    /**
     *  프론트 단에서 파일을 받아 확장자에 따라 파일 타입을 결정
     *      - String으로 할지 Enum으로 할지 결정 필요
     * */
    private String getFileType(MultipartFile files){
        String fileName = files.getOriginalFilename();
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        if(extension.equals("mp4") || extension.equals("MP4"))
            return "VIDEO";

        extension = extension.toLowerCase();
        if(extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png"))
            return "IMAGE";

        if(extension.equals("AVI"))
            return "VIDEO";

        return null;
    }

    @PutMapping("/user/account/{userNo}/pwd")
    public Result updatePwd(@PathVariable("userNo") int user_no, @RequestBody updateUserRequest request){
        User findUser = userService.findUser(user_no);
        System.out.println(bCryptPasswordEncoder.matches(request.getUser_pwd(), findUser.getUser_pwd()));
        if(!bCryptPasswordEncoder.matches(request.getUser_pwd(), findUser.getUser_pwd())){
            return new Result(false, HttpStatus.NO_CONTENT.value(), new UserDto());
        }

        String newpwd = bCryptPasswordEncoder.encode(request.getUser_newpwd());
        System.out.println(newpwd);
        userService.updatePwd(user_no, newpwd);
        User user = userService.findUser(user_no);
        if(user != null) {
            return new Result(true, HttpStatus.OK.value(), new UserDto(user));
        }else{
            return new Result(false, HttpStatus.BAD_REQUEST.value(), new UserDto());
        }
    }

    @DeleteMapping("/user/account/{userNo}")
    public Result deleteUser(@PathVariable("userNo") int user_no){
        userService.deleteUser(user_no);
        User user = userService.findUser(user_no);
        if(user == null) {
            return new Result(true, HttpStatus.OK.value());
        }else{
            return new Result(false, HttpStatus.BAD_REQUEST.value());
        }
    }

    @PostMapping("/confirm/email")
    public Result confirmEmail(@RequestBody confirmUserRequest request){
        boolean result = userService.validateDuplicateEmail(request.getUser_email());
        if(result) {
            return new Result(true, HttpStatus.OK.value(), true);
        }else{
            return new Result(true, HttpStatus.OK.value(), false);
        }
    }

    @PostMapping("/confirm/nickname")
    public Result confirmNickname(@RequestBody confirmUserRequest request){
        boolean result = userService.validateDuplicateNickname(request.getUser_nickname());
        if(result) {
            return new Result(true, HttpStatus.OK.value(), true);
        }else{
            return new Result(true, HttpStatus.OK.value(), false);
        }
    }

    @Data
    static class joinUserRequest{
        private String user_email;
        private String user_pwd;
        private String user_nickname;
    }

    @Data
    static class confirmUserRequest{
        private String user_email;
        private String user_nickname;
    }

    @Data
    static class updateUserRequest{
        private String user_nickname;
        private String user_pwd;
        private String user_newpwd;
        private MultipartFile file;
        private String user_title;
    }

    @Data
    static class loginUserRequest{
        private String user_email;
        private String user_pwd;
    }

    @Data
    static class interestDto{
        private int interest_no;
        private int tag_no;
        private String tag_content;

        public interestDto(Interest interest) {
            this.interest_no = interest.getInterest_no();
            this.tag_no = interest.getTag().getTag_no();
            this.tag_content = interest.getTag().getTag_content();
        }
    }

    @Data
    static class titleDto{
        private String title_name;

        public titleDto(Title title) {
            this.title_name = title.getTitle_name();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class UserDto{
        private int user_no;
        private String user_email;
        private String user_nickname;
        private String user_title;
        private int file_no;
        private List<titleDto> titles;
        private List<interestDto> interests;

        public UserDto(User user) {
            this.user_no = user.getUser_no();
            this.user_email = user.getUser_email();
            this.user_nickname = user.getUser_nickname();
            this.user_title = user.getUser_title();
            if(user.getMedia() == null){
                this.file_no = 0;
            }else{
                this.file_no = user.getMedia().getFile_no();
            }
        }

        public UserDto(User user, List<titleDto> titles) {
            this.user_no = user.getUser_no();
            this.user_email = user.getUser_email();
            this.user_nickname = user.getUser_nickname();
            this.user_title = user.getUser_title();
            this.titles = titles;
            if(user.getMedia() == null){
                this.file_no = 0;
            }else{
                this.file_no = user.getMedia().getFile_no();
            }
        }

        public UserDto(User user, List<titleDto> titles, List<interestDto> interests) {
            this.user_no = user.getUser_no();
            this.user_email = user.getUser_email();
            this.user_nickname = user.getUser_nickname();
            this.user_title = user.getUser_title();
            this.titles = titles;
            this.interests = interests;
            if(user.getMedia() == null){
                this.file_no = 0;
            }else{
                this.file_no = user.getMedia().getFile_no();
            }
        }

        @Data
        @AllArgsConstructor
        public class Result<T> {
            private boolean isSuccess;
            private int code;
            private T data;

            public Result(boolean isSuccess, int code) {
                this.isSuccess = isSuccess;
                this.code = code;
            }
        }

    }
}