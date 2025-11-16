# QueryDSL QType이 common-module의 BaseEntity 상속 필드를 인식하지 못하는 문제

* **🗓️ 발생 일시:** 2025/11/13
* **👨‍💻 담당자:** 허준형
* **🏷️ 관련 서비스:** `portfolio-service`, `common-module`

---

## 🐛 이슈 발생

### 현상 요약

`portfolio-service`에 QueryDSL을 도입한 시점부터, `QPortfolioEntity`와 같은 QType 엔티티가 공통 모듈(`common-module`)의 `BaseEntity`로부터 상속받은 `createdAt` 및 `lastModifiedAt` 필드를 제대로 인식하지 못하는 문제가 발생함.

IDE에서 생성된 QType 파일을 열어보면, 해당 필드들이 존재하지 않거나 `unmapped` 상태로 표시되어 컴파일 오류가 발생함.

### 재현 순서

1.  `common-module`에 대해 `mvn clean compile`을 실행한다.
2.  `portfolio-service`에서 `mvn clean compile`을 수행한다.
3.  `portfolio-service`의 `target/generated-sources/annotations` 경로에 생성된 `QPortfolioEntity.java` 파일을 확인한다.
4.  `createdAt`, `lastModifiedAt` 필드 관련 코드에서 컴파일 오류(빨간색 글씨)가 발생하는 것을 확인한다.

---

## 🧐 원인 분석

`BaseEntity`는 `@MappedSuperclass` 어노테이션을 사용하며 `common-module`에 위치하고 있다. `portfolio-service`의 엔티티(예: `PortfolioEntity`)가 이를 상속받을 때, QueryDSL의 어노테이션 프로세서(APT)가 상속 관계를 올바르게 인식하기 위해서는 두 모듈 모두에 정밀한 설정이 필요하다.

사용자의 초기 분석대로, `common-module` 자체도 QueryDSL 어노테이션 프로세싱의 대상이 되어야 하며, `portfolio-service`는 `common-module`을 '참조'할 수 있어야 한다.

---

## ✅ 해결 방안

### 조치 1: `common-module`의 `pom.xml`에 QueryDSL 설정 추가

`@MappedSuperclass`(`BaseEntity`)를 QType으로 올바르게 인식시키기 위해, `common-module`의 `pom.xml`에 `querydsl-apt` 및 `querydsl-jpa` 의존성을 추가한다.

가장 중요한 것은 `maven-compiler-plugin` 설정에 **`-Aquerydsl.mappedSuperclass=true`** 컴파일러 인수를 추가하여, 상속된 엔티티도 QueryDSL 처리 대상에 포함되도록 명시하는 것이다.

```
<dependencies>
    ...
    <dependency>
        <groupId>com.querydsl</groupId>
        <artifactId>querydsl-jpa</artifactId>
        <classifier>jakarta</classifier>
        <version>${querydsl.version}</version>
    </dependency>
    <dependency>
        <groupId>com.querydsl</groupId>
        <artifactId>querydsl-apt</artifactId>
        <classifier>jakarta</classifier>
        <version>${querydsl.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <compilerArgs>
                    <arg>-Aquerydsl.mappedSuperclass=true</arg>
                </compilerArgs>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.querydsl</groupId>
                        <artifactId>querydsl-apt</artifactId>
                        <classifier>jakarta</classifier>
                        <version>${querydsl.version}</version>
                    </path>
                    ...
                </annotationProcessorPaths>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

### 조치 2: portfolio-service의 pom.xml에 common-module 참조 추가

portfolio-service가 컴파일될 때 BaseEntity의 존재를 인식할 수 있도록, maven-compiler-plugin의 annotationProcessorPaths에 common-module의 경로를 명시적으로 추가해야 한다.

portfolio-service 역시 상속을 처리해야 하므로 -Aquerydsl.mappedSuperclass=true 인수가 동일하게 필요하다.

```
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <compilerArgs>
                    <arg>-Aquerydsl.mappedSuperclass=true</arg>
                </compilerArgs>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.example</groupId>
                        <artifactId>common-module</artifactId>
                        <version>${project.version}</version>
                    </path>
                    
                    ...
                </annotationProcessorPaths>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

---

## 📝 후속 조치 및 교훈
* @MappedSuperclass를 통해 공통 엔티티 필드를 상속하는 다중 모듈 환경에서 QueryDSL을 사용하려면, **상속을 제공하는 모듈(common-module)**과 상속을 받는 모듈(portfolio-service) 양쪽 모두의 maven-compiler-plugin 설정이 중요하다.

* 단순히 의존성을 추가하는 것을 넘어, -Aquerydsl.mappedSuperclass=true 컴파일러 인수를 양쪽 모두에 적용해야 상속 필드가 정상적으로 QType에 반영된다.

* 상속을 받는 모듈(portfolio-service)은 annotationProcessorPaths에 상속을 제공하는 모듈(common-module)을 명시적으로 포함해야 컴파일 시점(APT)에 BaseEntity를 찾을 수 있다.