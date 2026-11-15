package catalogo;

import org.springframework.boot.SpringApplication;

public class TestCatalogoApplication {

	public static void main(String[] args) {
		SpringApplication.from(CatalogoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
