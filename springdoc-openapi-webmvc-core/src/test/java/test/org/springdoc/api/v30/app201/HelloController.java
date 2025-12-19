/*
 *
 *  *
 *  *  *
 *  *  *  * Copyright 2019-2022 the original author or authors.
 *  *  *  *
 *  *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  *  * you may not use this file except in compliance with the License.
 *  *  *  * You may obtain a copy of the License at
 *  *  *  *
 *  *  *  *      https://www.apache.org/licenses/LICENSE-2.0
 *  *  *  *
 *  *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  *  * See the License for the specific language governing permissions and
 *  *  *  * limitations under the License.
 *  *  *
 *  *
 *
 */

package test.org.springdoc.api.v30.app201;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to test static inner classes in request/response DTOs.
 */
@RestController
public class HelloController {

	@PostMapping("/userRequest")
	public UserDTO.Response createUser(@RequestBody UserDTO.Request request) {
		UserDTO.Response response = new UserDTO.Response();
		response.setUserId("user-123");
		response.setMessage("User created successfully");
		return response;
	}

	@PostMapping("/productRequest")
	public ProductDTO.Response createProduct(@RequestBody ProductDTO.Request request) {
		ProductDTO.Response response = new ProductDTO.Response();
		response.setProductId("prod-456");
		response.setMessage("Product created successfully");
		return response;
	}

	@GetMapping("/userResponse")
	public UserDTO.Response getUser() {
		UserDTO.Response response = new UserDTO.Response();
		response.setUserId("user-123");
		response.setMessage("User retrieved");
		return response;
	}

	@GetMapping("/productResponse")
	public ProductDTO.Response getProduct() {
		ProductDTO.Response response = new ProductDTO.Response();
		response.setProductId("prod-456");
		response.setMessage("Product retrieved");
		return response;
	}
}

