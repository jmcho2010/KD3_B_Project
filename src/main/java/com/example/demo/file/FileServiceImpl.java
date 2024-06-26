package com.example.demo.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.auth.AuthRequest;
import com.example.demo.user.User;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Service
public class FileServiceImpl implements FileService {

	@Value("${spring.cloud.gcp.storage.bucket}")
	String bucketName;

	@Autowired
	FileMapper fileMapper;

	@Override
	public String uploadFiles(UploadFile uploadFile){
		try {
			MultipartFile multipartFile = uploadFile.getFile();
			// 파일이 없는 경우
			if (multipartFile == null || multipartFile.isEmpty()) {
				// 사용자에게 알림 메시지 전송
				throw new IllegalArgumentException("업로드할 파일이 없습니다.");
			}
			// 버킷 저장소로 보내는 곳
			String keyFileName = "projectb-419201-70f6627fba41.json";
			InputStream keyFile = ResourceUtils.getURL("classpath:" + keyFileName).openStream();
			Storage storage = StorageOptions.newBuilder().setCredentials(GoogleCredentials.fromStream(keyFile)).build()
					.getService();
			// 파일의 원래 이름
			String originalFilename = multipartFile.getOriginalFilename();
			String fileName = StringUtils.cleanPath(originalFilename);
			// 파일을 uuid로 작성한 이름
			String objectName = UUID.randomUUID().toString() + "_" + fileName;
			
			BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
					.setContentType(multipartFile.getContentType()).build();
			Blob blob = storage.create(blobInfo, uploadFile.getFile().getInputStream());
			
			// 파일의 저장 주소(접근 경로)
			String uri = UriComponentsBuilder.newInstance().scheme("https").host("storage.googleapis.com")
					.path("/" + bucketName + "/" + objectName).build().toUriString();
			
			// 데이터베이스에 올라갈 정보 설정
			UploadFile fileInfo = new UploadFile();
	
			fileInfo.setPath("https://storage.googleapis.com/" + bucketName);
			fileInfo.setOriName(originalFilename);
			fileInfo.setSaveName(objectName);
			fileInfo.setUri(uri);
			fileInfo.setUserID(uploadFile.getUserID());
			// 데이터베이스에 정보 입력
			fileMapper.insertFile(fileInfo);
	
			return objectName;// uuid로 반환
		} catch (FileNotFoundException e) {
	        // 파일을 찾을 수 없는 경우
	        e.printStackTrace();
	        return "파일을 찾을 수 없습니다";
	    } catch (IOException e) {
	        // 파일 입출력 예외 처리
	        e.printStackTrace();
	        return "파일 입출력에 오류가 발생했습니다";
	    }
	}

	// 파일의 링크 가져오기
	@Override
	public String getDownLink(String saveName) {
		// uuid에서 인코딩
	    String encodedFilename = URLEncoder.encode(saveName, StandardCharsets.UTF_8)
	    		.replaceAll("\\+", "%20")
	    		.replaceAll("\\%21", "!")
	            .replaceAll("\\%27", "'")
	            .replaceAll("\\%28", "(")
	            .replaceAll("\\%29", ")")
	            .replaceAll("\\%7E", "~");
	    // 인코딩된 파일 주소 반환
		return "https://storage.googleapis.com/" + bucketName + "/" + encodedFilename;
	}

	// 유저아이디로 파일 리스트 가져오기
	@Override
	public List<UploadFile> getFilesByUserID(String userID) {
		return fileMapper.getFileUserID(userID);
	}
	
	// 버킷에서 파일 삭제
	@Override
	public void deleteFileBucket(String saveName){
		try {
			// 파일 이름 추출
			String[] part=saveName.split("_");
			if (part.length < 2) {
	            throw new IllegalArgumentException("잘못된 파일 이름 형식입니다.");
	        }
			String oriName=part[1];
			System.out.println(oriName);
			// 저장소 정보
			String keyFileName = "projectb-419201-70f6627fba41.json";
			InputStream keyFile = ResourceUtils.getURL("classpath:" + keyFileName).openStream();
		    Storage storage = StorageOptions.newBuilder().setCredentials(GoogleCredentials.fromStream(keyFile)).build()
		            .getService();
			
			BlobId blobId = BlobId.of(bucketName, oriName);
			
			// gcp에서 파일 삭제
			boolean deleted = storage.delete(blobId);
			if (deleted) {
	//			fileMapper.deleteFileSaveName(oriName);
				System.out.println("파일 삭제 성공");
			} else {
				throw new IOException("파일 삭제 실패");
			}
		}catch (FileNotFoundException e) {
	        // 파일을 찾을 수 없는 경우
		    e.printStackTrace();
	    } catch (IOException e) {
	        // 파일 삭제 실패한 경우
	    	e.printStackTrace();
	    } catch (IllegalArgumentException e) {
	        // 잘못된 파일 이름 형식인 경우
	        e.printStackTrace();
	    }
		
	}

	// 데이터베이스에서 파일 정보 삭제
	@Override
	public void deleteFileDB(String saveName) {
		try {
			// 데이터베이스에서 찾기
	        String storedSaveName = fileMapper.findSaveName(saveName);
	        
	        if (storedSaveName != null) {
	        	// 데이터베이스 삭제
	            fileMapper.deleteFileSaveName(storedSaveName);
//	            System.out.println("데이터베이스에서 파일 삭제했습니다.");
	        } else {
//	            System.out.println("UUID에 해당하는 파일이 데이터베이스에 없습니다.");
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
//	        throw new RuntimeException("데이터베이스에서 파일을 삭제하는 동안 오류가 발생했습니다.");
	    }
	}

	// 파일 uuid로 파일 아이디 가져오기
	@Override
	public Integer findFileID(String saveName) {
		return fileMapper.findFileID(saveName);
	}

	// 파일아이디로 가져온 정보로 링크 만드는건데 방식을 바꿔서 안씀
	@Override
	public String getLinkByFileID(Integer fileID) {
		
		return fileMapper.getLinkByFileID(fileID);
	}

	
}
