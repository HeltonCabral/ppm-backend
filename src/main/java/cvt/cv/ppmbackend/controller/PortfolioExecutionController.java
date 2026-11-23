package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.PortfolioExecutionDtos.Response;
import cvt.cv.ppmbackend.service.PortfolioExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
public class PortfolioExecutionController {
    private final PortfolioExecutionService portfolio;

    public PortfolioExecutionController(PortfolioExecutionService portfolio) { this.portfolio = portfolio; }

    @GetMapping("/execution")
    public Response execution() { return portfolio.execution(); }
}
