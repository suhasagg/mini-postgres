package com.example.minipostgres.planner;

import com.example.minipostgres.storage.Table;
import java.util.*;

public interface Plan {
    String description();
    List<Long> candidateRowIds(Table table);
}
